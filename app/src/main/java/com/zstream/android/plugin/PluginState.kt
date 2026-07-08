package com.zstream.android.plugin

/**
 * Represents the lifecycle state of the plugin across the whole app.
 * Emitted by PluginManager.pluginState.
 */
sealed class PluginState {
    /** Initial transient state while the app checks for / loads the plugin. */
    object Loading : PluginState()

    /** No plugin has been installed yet. Show the install screen. */
    object NotInstalled : PluginState()

    /**
     * Plugin is loaded and ready to resolve streams. [plugin] is the raw Entry instance — the
     * app has no compiled contract with it and only ever calls it via reflection.
     */
    data class Ready(
        val plugin: Any,
        val version: Int,
    ) : PluginState()

    /**
     * Plugin is loaded and working, but a newer version has been staged.
     * The staged plugin activates on the next app launch.
     */
    data class UpdateAvailable(
        val plugin: Any,
        val currentVersion: Int,
        val stagedVersion: Int,
    ) : PluginState()

    /** The plugin file exists but failed to load or verify. Treat as not installed. */
    data class Failed(val reason: String) : PluginState()
}
