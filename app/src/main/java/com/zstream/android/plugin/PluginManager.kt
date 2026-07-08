package com.zstream.android.plugin

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.zstream.android.BuildConfig
import com.zstream.android.di.PluginDataStore
import com.zstream.plugin.api.MediaRequest
import com.zstream.plugin.api.SourceInfo
import com.zstream.plugin.api.StreamPlugin
import com.zstream.plugin.api.StreamResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PluginManager"

internal val KEY_ACTIVE_META  = stringPreferencesKey("plugin_active_meta")
internal val KEY_STAGED_META  = stringPreferencesKey("plugin_staged_meta")
internal val KEY_HIGHEST_VER  = intPreferencesKey("plugin_highest_version")
internal val KEY_LAST_CHECKED = stringPreferencesKey("plugin_last_checked")

/**
 * Central singleton that owns the loaded plugin instance and manages its lifecycle.
 *
 * Startup sequence (called from ZStreamApp.onCreate):
 *  1. Emit Loading
 *  2. activateStagedUpdate() — promote staged to active if staged.version > active.version
 *  3. Read active metadata from DataStore
 *  4. If missing → emit NotInstalled
 *  5. Verify file exists + hash + signature
 *  6. Load via PluginLoader
 *  7. Emit Ready
 *  8. Launch background checkForUpdate()
 *
 * Silent update flow:
 *  download → verify → stage → next launch activates automatically
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loader: PluginLoader,
    private val updateChecker: PluginUpdateChecker,
    @PluginDataStore internal val dataStore: DataStore<Preferences>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

    private val _pluginState = MutableStateFlow<PluginState>(PluginState.Loading)
    val pluginState: StateFlow<PluginState> = _pluginState.asStateFlow()

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Must be called once at app startup (from ZStreamApp.onCreate).
     * Runs the full startup sequence described in the class doc.
     */
    fun initialize() {
        scope.launch {
            _pluginState.value = PluginState.Loading
            try {
                activateStagedUpdate()
                val meta = readActiveMeta()
                if (meta == null) {
                    _pluginState.value = PluginState.NotInstalled
                    return@launch
                }
                val file = loader.pluginDir().resolve(meta.fileName)
                if (!verifyActivePlugin(file, meta)) {
                    Log.w(TAG, "Active plugin failed verification — treating as not installed")
                    clearActiveMeta()
                    _pluginState.value = PluginState.NotInstalled
                    return@launch
                }
                val plugin = loader.load(file)
                _pluginState.value = PluginState.Ready(plugin, meta.version)
                Log.i(TAG, "Plugin v${meta.version} loaded")
                checkForUpdateInBackground(meta.version)
            } catch (e: Exception) {
                Log.e(TAG, "Plugin init failed: ${e.message}", e)
                _pluginState.value = PluginState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Debug sideload (DEBUG builds only)
    // -------------------------------------------------------------------------

    /**
     * Loads a plugin APK directly from [path] without downloading or verifying hash/signature.
     * Usage: adb push plugin-debug.apk /sdcard/Download/plugin-debug.apk
     *        then call this from the dev menu or PluginGateViewModel.
     */
    suspend fun debugSideload(path: String) = withContext(Dispatchers.IO) {
        val src = java.io.File(path)
        check(src.exists()) { "Sideload file not found: $path" }
        val dest = loader.pluginDir().resolve("plugin-v0.apk")
        src.copyTo(dest, overwrite = true)
        val meta = PluginMetadata(
            version     = 0,
            hash        = "sha256:dev",
            fileName    = dest.name,
            activatedAt = System.currentTimeMillis() / 1000,
        )
        writeActiveMeta(meta)
        val plugin = loader.load(dest)
        _pluginState.value = PluginState.Ready(plugin, 0)
        Log.i(TAG, "Debug sideload complete: $path")
    }

    // -------------------------------------------------------------------------
    // Manual install (first time)
    // -------------------------------------------------------------------------

    /**
     * Called from PluginGateViewModel when the user taps "Install Plugin".
     * Downloads, verifies, activates, and loads the plugin in one shot.
     * [onProgress] receives 0f–1f.
     */
    suspend fun manualInstall(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val manifest = updateChecker.fetchManifest()
        val highestSeen = readHighestSeenVersion()
        loader.checkRollback(manifest.latestVersion, highestSeen)

        val file = loader.download(manifest.downloadUrl, manifest.latestVersion, manifest.hash, onProgress)

        if (!loader.verifySignature(file)) {
            file.delete()
            throw SecurityException("Plugin APK signature verification failed")
        }

        val meta = PluginMetadata(
            version     = manifest.latestVersion,
            hash        = manifest.hash,
            fileName    = file.name,
            activatedAt = System.currentTimeMillis() / 1000,
        )
        writeActiveMeta(meta)
        markHighestSeenVersion(manifest.latestVersion)

        val plugin = loader.load(file)
        _pluginState.value = PluginState.Ready(plugin, meta.version)
        Log.i(TAG, "Plugin v${meta.version} installed manually")
    }

    // -------------------------------------------------------------------------
    // Background update check
    // -------------------------------------------------------------------------

    fun checkForUpdateInBackground(currentVersion: Int) {
        scope.launch {
            try {
                checkForUpdate(currentVersion)
            } catch (e: Exception) {
                Log.w(TAG, "Background update check failed: ${e.message}")
            }
        }
    }

    /**
     * Checks the manifest and, if a newer version is available, downloads and stages it.
     * The staged plugin is activated on the next app launch. Never hot-swaps.
     *
     * Safe to call from Settings "Check for update" — same code path as background check.
     * Returns the staged version number if an update was staged, null if already up to date.
     */
    suspend fun checkForUpdate(currentVersion: Int? = null): Int? = withContext(Dispatchers.IO) {
        val activeVersion = currentVersion
            ?: (pluginState.value as? PluginState.Ready)?.version
            ?: (pluginState.value as? PluginState.UpdateAvailable)?.currentVersion
            ?: return@withContext null

        val manifest = updateChecker.fetchManifest()
        writeLastChecked()

        if (manifest.latestVersion <= activeVersion) {
            Log.i(TAG, "Plugin up to date (v$activeVersion)")
            return@withContext null
        }

        val highestSeen = readHighestSeenVersion()
        if (manifest.latestVersion < highestSeen) {
            Log.w(TAG, "Manifest rollback refused (v${manifest.latestVersion} < highest seen v$highestSeen)")
            return@withContext null
        }

        if (manifest.minAppVersion > BuildConfig.VERSION_CODE) {
            Log.w(TAG, "Plugin v${manifest.latestVersion} requires app v${manifest.minAppVersion}, skipping")
            return@withContext null
        }

        Log.i(TAG, "Staging plugin update: v$activeVersion → v${manifest.latestVersion}")
        val file = loader.download(manifest.downloadUrl, manifest.latestVersion, manifest.hash)

        if (!loader.verifySignature(file)) {
            file.delete()
            Log.w(TAG, "Staged plugin failed signature check — discarding")
            return@withContext null
        }

        val staged = PluginMetadata(
            version     = manifest.latestVersion,
            hash        = manifest.hash,
            fileName    = file.name,
            activatedAt = System.currentTimeMillis() / 1000,
        )
        writeStagedMeta(staged)

        // Update UI state so Settings can show "update ready"
        val currentState = pluginState.value
        if (currentState is PluginState.Ready) {
            _pluginState.value = PluginState.UpdateAvailable(
                plugin         = currentState.plugin,
                currentVersion = currentState.version,
                stagedVersion  = staged.version,
            )
        }

        Log.i(TAG, "Plugin v${staged.version} staged — activates on next launch")
        staged.version
    }

    // -------------------------------------------------------------------------
    // Staged update promotion (called at startup before loading active)
    // -------------------------------------------------------------------------

    private suspend fun activateStagedUpdate() {
        val staged = readStagedMeta() ?: return
        val active = readActiveMeta()

        if (active != null && staged.version <= active.version) {
            clearStagedMeta()
            return
        }

        val highestSeen = readHighestSeenVersion()
        if (staged.version < highestSeen) {
            Log.w(TAG, "Staged plugin rollback refused — clearing")
            clearStagedMeta()
            return
        }

        val stagedFile = loader.pluginDir().resolve(staged.fileName)
        if (!stagedFile.exists()
            || !loader.verifyHash(stagedFile, staged.hash)
            || !loader.verifySignature(stagedFile)
        ) {
            Log.w(TAG, "Staged plugin failed verification — discarding")
            stagedFile.delete()
            clearStagedMeta()
            return
        }

        try {
            loader.load(stagedFile)
        } catch (e: Throwable) {
            Log.w(TAG, "Staged plugin failed load validation — discarding", e)
            stagedFile.delete()
            clearStagedMeta()
            return
        }

        // Delete old active file
        active?.let { loader.pluginDir().resolve(it.fileName).delete() }

        writeActiveMeta(staged)
        markHighestSeenVersion(staged.version)
        clearStagedMeta()
        Log.i(TAG, "Staged plugin v${staged.version} promoted to active")
    }

    // -------------------------------------------------------------------------
    // Plugin API passthrough
    // -------------------------------------------------------------------------

    fun availableSources(): List<SourceInfo> =
        when (val s = pluginState.value) {
            is PluginState.Ready -> s.plugin.availableSources()
            is PluginState.UpdateAvailable -> s.plugin.availableSources()
            else -> emptyList()
        }

    suspend fun resolve(media: MediaRequest, sourceId: String): StreamResult {
        val plugin: StreamPlugin = when (val s = pluginState.value) {
            is PluginState.Ready -> s.plugin
            is PluginState.UpdateAvailable -> s.plugin
            else -> return StreamResult.Error("Plugin not ready")
        }
        return try {
            resolveReflective(plugin, media, sourceId)
        } catch (t: Throwable) {
            Log.e(TAG, "Plugin resolve threw unexpectedly: ${t.message}", t)
            StreamResult.Error(t.message ?: "Plugin error")
        }
    }

    private suspend fun resolveReflective(
        plugin: StreamPlugin,
        media: MediaRequest,
        sourceId: String,
    ): StreamResult {
        return try {
            val resolveMethods = plugin.javaClass.methods.filter { it.name == "resolve" }
            Log.w(
                TAG,
                "Legacy resolve candidates: " + resolveMethods.joinToString(" | ") { method ->
                    val params = method.parameterTypes.joinToString(",") { it.name }
                    "${method.declaringClass.name}#${method.name}($params)"
                },
            )
            val sourceAwareMethod = plugin.javaClass.methods.firstOrNull {
                it.name == "resolve" &&
                    it.parameterTypes.size == 3 &&
                    it.parameterTypes[0].name == MediaRequest::class.java.name &&
                    it.parameterTypes[1] == String::class.java
            }
            if (sourceAwareMethod != null) {
                @Suppress("UNCHECKED_CAST")
                return suspendCoroutineUninterceptedOrReturn<StreamResult> { continuation ->
                    when (val result = sourceAwareMethod.invoke(plugin, media, sourceId, continuation)) {
                        null -> StreamResult.Error("Plugin returned null")
                        COROUTINE_SUSPENDED -> COROUTINE_SUSPENDED
                        else -> result as StreamResult
                    }
                }
            }

            val legacyMethod = plugin.javaClass.methods.firstOrNull {
                it.name == "resolve" &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0].name == MediaRequest::class.java.name
            } ?: return StreamResult.Error("Plugin is incompatible with this app version")

            @Suppress("UNCHECKED_CAST")
            suspendCoroutineUninterceptedOrReturn<StreamResult> { continuation ->
                when (val result = legacyMethod.invoke(plugin, media, continuation)) {
                    null -> StreamResult.Error("Plugin returned null")
                    COROUTINE_SUSPENDED -> COROUTINE_SUSPENDED
                    else -> result as StreamResult
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Reflective plugin resolve threw unexpectedly: ${t.message}", t)
            StreamResult.Error(t.cause?.message ?: t.message ?: "Plugin error")
        }
    }

    // -------------------------------------------------------------------------
    // Convenience accessors for UI
    // -------------------------------------------------------------------------

    fun pluginVersion(): Int? = when (val s = pluginState.value) {
        is PluginState.Ready -> s.version
        is PluginState.UpdateAvailable -> s.currentVersion
        else -> null
    }

    fun stagedVersion(): Int? =
        (pluginState.value as? PluginState.UpdateAvailable)?.stagedVersion

    suspend fun readLastChecked(): Long? =
        dataStore.data.first()[KEY_LAST_CHECKED]?.toLongOrNull()

    // -------------------------------------------------------------------------
    // DataStore helpers
    // -------------------------------------------------------------------------

    private suspend fun readActiveMeta(): PluginMetadata? =
        dataStore.data.first()[KEY_ACTIVE_META]
            ?.let { runCatching { gson.fromJson(it, PluginMetadata::class.java) }.getOrNull() }

    private suspend fun writeActiveMeta(meta: PluginMetadata) =
        dataStore.edit { it[KEY_ACTIVE_META] = gson.toJson(meta) }

    private suspend fun clearActiveMeta() =
        dataStore.edit { it.remove(KEY_ACTIVE_META) }

    private suspend fun readStagedMeta(): PluginMetadata? =
        dataStore.data.first()[KEY_STAGED_META]
            ?.let { runCatching { gson.fromJson(it, PluginMetadata::class.java) }.getOrNull() }

    private suspend fun writeStagedMeta(meta: PluginMetadata) =
        dataStore.edit { it[KEY_STAGED_META] = gson.toJson(meta) }

    private suspend fun clearStagedMeta() =
        dataStore.edit { it.remove(KEY_STAGED_META) }

    private suspend fun readHighestSeenVersion(): Int =
        dataStore.data.first()[KEY_HIGHEST_VER] ?: 0

    private suspend fun markHighestSeenVersion(version: Int) {
        val current = readHighestSeenVersion()
        if (version > current) dataStore.edit { it[KEY_HIGHEST_VER] = version }
    }

    private suspend fun writeLastChecked() =
        dataStore.edit { it[KEY_LAST_CHECKED] = System.currentTimeMillis().toString() }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun verifyActivePlugin(file: File, meta: PluginMetadata): Boolean {
        if (!file.exists()) { Log.w(TAG, "Plugin file missing: ${file.name}"); return false }
        if (meta.hash == "sha256:dev") return true  // sideloaded dev build — skip verification
        if (!loader.verifyHash(file, meta.hash)) return false
        if (!loader.verifySignature(file)) return false
        return true
    }
}
