package com.zstream.android.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.zstream.android.data.CryptoUtils.toBase64Url
import com.zstream.android.data.remote.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

private const val TAG = "AccountRepo"
private val Context.accountStore by preferencesDataStore("account")

data class AccountSession(val userId: String, val token: String, val nickname: String, val deviceName: String = "", val usesPasskey: Boolean = false)

/** A cached login, kept around so a TV can switch between multiple accounts without re-authenticating. */
data class SavedProfile(
    val id: String,
    val userId: String,
    val token: String,
    val nickname: String,
    val deviceName: String,
    val usesPasskey: Boolean,
    val lastActiveAt: Long,
    val kidsModeEnabled: Boolean = false,
)

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val api: BackendApi,
) {
    private val KEY_TOKEN    = stringPreferencesKey("token")
    private val KEY_USER_ID  = stringPreferencesKey("user_id")
    private val KEY_NICKNAME = stringPreferencesKey("nickname")
    private val KEY_DEVICE   = stringPreferencesKey("device_name")
    private val KEY_PASSKEY  = booleanPreferencesKey("uses_passkey")
    private val KEY_GUEST_ID = stringPreferencesKey("guest_id")
    private val KEY_SAVED_PROFILES = stringPreferencesKey("saved_profiles")
    private val gson = Gson()
    private val guestIdMutex = Mutex()

    // Current session (can be null)
    var currentSession: AccountSession? = null
        private set

    val session: Flow<AccountSession?> = ctx.accountStore.data.map { prefs ->
        val token      = prefs[KEY_TOKEN]    ?: return@map null
        val userId     = prefs[KEY_USER_ID]  ?: return@map null
        val nickname   = prefs[KEY_NICKNAME] ?: ""
        val deviceName = prefs[KEY_DEVICE]   ?: ""
        val account = AccountSession(userId, token, nickname, deviceName, prefs[KEY_PASSKEY] ?: false)
        currentSession = account
        account
    }

    /** Stable namespace for device-local data that must not leak across account/profile switches. */
    val activeReleaseOwner: Flow<String> = session
        .map { account -> account?.userId ?: "guest:${getOrCreateGuestId()}" }
        .distinctUntilChanged()

    suspend fun currentReleaseOwner(): String = activeReleaseOwner.first()

    /** Cached logins available for a fast profile switch. TV-only feature; harmless if unused on phone. */
    val savedProfiles: Flow<List<SavedProfile>> = ctx.accountStore.data.map { prefs ->
        val raw = prefs[KEY_SAVED_PROFILES] ?: return@map emptyList()
        runCatching {
            val type = object : TypeToken<List<SavedProfile>>() {}.type
            gson.fromJson<List<SavedProfile>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun savedProfilesSnapshot(): List<SavedProfile> = savedProfiles.first()

    /** Switches the active session to an already-saved profile without re-authenticating. */
    suspend fun activateProfile(profile: SavedProfile) {
        persist(AccountSession(profile.userId, profile.token, profile.nickname, profile.deviceName, profile.usesPasskey))
    }

    suspend fun removeProfile(id: String) {
        val current = savedProfilesSnapshot().filterNot { it.id == id }
        ctx.accountStore.edit { it[KEY_SAVED_PROFILES] = gson.toJson(current) }
    }

    /** TV-only: persists the Kids Mode flag against a specific cached profile, keyed by userId. */
    suspend fun setProfileKidsMode(userId: String, enabled: Boolean) {
        val current = savedProfilesSnapshot().toMutableList()
        val idx = current.indexOfFirst { it.userId == userId }
        if (idx < 0) return
        current[idx] = current[idx].copy(kidsModeEnabled = enabled)
        ctx.accountStore.edit { it[KEY_SAVED_PROFILES] = gson.toJson(current) }
    }

    private suspend fun upsertSavedProfile(account: AccountSession) {
        val current = savedProfilesSnapshot().toMutableList()
        val idx = current.indexOfFirst { it.userId == account.userId }
        // Preserve the existing kidsModeEnabled flag on re-login/reactivation -- only brand-new
        // entries start with Kids Mode off, so switching back to an existing profile never resets it.
        val existingKidsMode = if (idx >= 0) current[idx].kidsModeEnabled else false
        val entry = SavedProfile(account.userId, account.userId, account.token, account.nickname, account.deviceName, account.usesPasskey, System.currentTimeMillis(), existingKidsMode)
        if (idx >= 0) current[idx] = entry else current.add(entry)
        ctx.accountStore.edit { it[KEY_SAVED_PROFILES] = gson.toJson(current) }
    }

    //  Passphrase auth 

    suspend fun login(passphrase: String, deviceName: String = "Android"): AccountSession {
        Log.d(TAG, "login derive keys passphraseLength=${passphrase.length} leadingOrTrailingWhitespace=${passphrase != passphrase.trim()} deviceNameLength=${deviceName.length}")
        val keys = CryptoUtils.keysFromPassphrase(passphrase.trim())
        return challengeLogin(keys.publicKey.toBase64Url(), keys.privateKey, keys.seed, deviceName, false)
    }

    suspend fun register(passphrase: String, deviceName: String = "Android"): AccountSession {
        val keys = CryptoUtils.keysFromPassphrase(passphrase.trim())
        return challengeRegister(keys.publicKey.toBase64Url(), keys.privateKey, keys.seed, deviceName, false)
    }

    //  Passkey auth 

    suspend fun loginWithPasskey(activityContext: Context, deviceName: String = "Android"): AccountSession =
        passkeyLogin(activityContext, deviceName).also { persist(it) }

    /**
     * Real WebAuthn login: fetch server options -> run the authenticator assertion -> post it to
     * /login/verify, which returns a full session. Does NOT persist -- callers that want to log
     * this device in wrap it with persist(); TV sync hands the session to another device instead.
     *
     * MIGRATION BRIDGE (temporary): accounts created before the real-WebAuthn backend existed have
     * no record in webauthn_credentials, so /login/verify returns 401/404 for them. In that case we
     * fall back to the legacy scheme -- derive a keypair from the passkey's credential id and log in
     * through the challenge endpoints -- so those users keep their account. The passkey itself is
     * unchanged, so the same authenticator assertion yields the same credential id and thus the same
     * derived account as before. Remove once legacy passkey accounts are migrated.
     * See docs/passkey-migration-bridge.md.
     */
    private suspend fun passkeyLogin(activityContext: Context, deviceName: String): AccountSession {
        val opts = api.passkeyLoginOptions(PasskeyLoginOptionsBody())
        val assertionJson = CryptoUtils.passkeyAuthenticationResponse(activityContext, opts.options.toString())
        val assertion = JsonParser.parseString(assertionJson).asJsonObject
        return try {
            sessionFromVerify(api.passkeyLoginVerify(PasskeyLoginVerifyBody(opts.stateToken, assertion, deviceName)), deviceName)
        } catch (e: HttpException) {
            if (e.code() != 401 && e.code() != 404) throw e
            Log.i(TAG, "passkey login/verify ${e.code()} -> legacy credential-id fallback")
            val credentialId = assertion.get("id").asString
            val keys = CryptoUtils.keysFromSeed(CryptoUtils.pbkdf2(credentialId))
            challengeLogin(keys.publicKey.toBase64Url(), keys.privateKey, keys.seed, deviceName, usesPasskey = true, persistSession = false)
        }
    }

    /**
     * Authenticate the phone's passkey and return the resulting session WITHOUT logging this
     * device in -- used by TV sync so the session can be forwarded to the TV. Real WebAuthn keys
     * can't leave the authenticator, so we transfer the issued session rather than a derived seed.
     */
    suspend fun passkeyLoginForTransfer(activityContext: Context, deviceName: String): AccountSession =
        passkeyLogin(activityContext, deviceName)

    /** Persist a session obtained elsewhere (e.g. a phone passkey login forwarded to this TV). */
    suspend fun adoptSession(session: AccountSession) = persist(session)

    private fun sessionFromVerify(resp: LoginResponse, deviceName: String): AccountSession {
        val userId = resp.user?.id ?: resp.session.userId
        return AccountSession(userId, resp.token, resp.user?.nickname ?: "", deviceName, usesPasskey = true)
    }

    suspend fun registerWithPasskey(activityContext: Context, deviceName: String = "Android"): AccountSession {
        val opts = api.passkeyRegisterOptions(PasskeyRegisterOptionsBody(deviceName))
        val attestation = CryptoUtils.passkeyRegisterResponse(activityContext, opts.options.toString())
        val resp = api.passkeyRegisterVerify(
            PasskeyRegisterVerifyBody(opts.stateToken, JsonParser.parseString(attestation).asJsonObject)
        )
        return sessionFromVerify(resp, deviceName).also { persist(it) }
    }

    /** Signs out of the active session only -- other cached profiles on this device are left intact. */
    suspend fun logout() {
        val activeUserId = currentSession?.userId
        ctx.accountStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_NICKNAME)
            prefs.remove(KEY_DEVICE)
            prefs.remove(KEY_PASSKEY)
        }
        currentSession = null
        if (activeUserId != null) removeProfile(activeUserId)
    }

    /**
     * Get a persistent guest ID for anonymous watch party participation.
     */
    suspend fun getOrCreateGuestId(): String = guestIdMutex.withLock {
        val prefs = ctx.accountStore.data.first()
        val existing = prefs[KEY_GUEST_ID]
        if (existing != null) return@withLock existing

        val newId = java.util.UUID.randomUUID().toString()
        ctx.accountStore.edit { it[KEY_GUEST_ID] = newId }
        newId
    }

    /**
     * Renames the active account (backend's PATCH /users/{id}), then updates the persisted
     * session and cached profile entry so the new name survives an app restart / profile switch
     * without needing a fresh login.
     */
    suspend fun updateNickname(newNickname: String): AccountSession {
        val session = currentSession ?: error("No active session")
        val trimmed = newNickname.trim()
        val updated = api.updateUserProfile(session.userId, session.bearer(), UpdateUserProfileBody(trimmed))
        val newSession = session.copy(nickname = updated.nickname)
        persist(newSession)
        currentSession = newSession
        return newSession
    }

    //  Sync helpers

    suspend fun getProgress(session: AccountSession)  = api.getProgress(session.userId, session.bearer())
    suspend fun setProgress(session: AccountSession, input: ProgressInput) =
        api.setProgress(session.userId, input.tmdbId, session.bearer(), input)
    suspend fun getBookmarks(session: AccountSession) = api.getBookmarks(session.userId, session.bearer())
    suspend fun addBookmark(session: AccountSession, input: BookmarkInput) =
        api.addBookmark(session.userId, input.tmdbId, session.bearer(), input)
    suspend fun removeBookmark(session: AccountSession, tmdbId: String) =
        api.removeBookmark(session.userId, tmdbId, session.bearer())

    //  Private 

    private suspend fun challengeLogin(pubB64: String, privKey: ByteArray, seed: ByteArray, device: String, usesPasskey: Boolean, persistSession: Boolean = true): AccountSession {
        Log.d(TAG, "login/start pubKey=$pubB64")
        val challenge = runCatching { api.loginStart(LoginStartBody(pubB64)) }.onFailure {
            val http = it as? HttpException
            Log.w(TAG, "login/start failed http=${http?.code()} backend=${http?.response()?.errorBody()?.string()} error=${it.message}", it)
        }.getOrThrow()
        Log.d(TAG, "login/start challenge=${challenge.challenge}")
        val sig = CryptoUtils.signChallenge(challenge.challenge, privKey)
        val encDevice = CryptoUtils.encryptData(device, seed)
        Log.d(TAG, "login/complete sig=${sig.take(20)}… device(enc)=${encDevice.take(20)}…")
        val resp = runCatching {
            api.loginComplete(LoginCompleteBody(pubB64, ChallengePayload(challenge.challenge, sig), encDevice))
        }.onFailure {
            val http = it as? HttpException
            Log.w(TAG, "login/complete failed http=${http?.code()} backend=${http?.response()?.errorBody()?.string()} error=${it.message}", it)
        }.getOrThrow()
        val userId = resp.user?.id ?: resp.session.userId
        Log.d(TAG, "login/complete resp token=${resp.token.take(20)}… userId=$userId session=${resp.session}")
        return AccountSession(userId, resp.token, resp.user?.nickname ?: "", device, usesPasskey).also { if (persistSession) persist(it) }
    }

    private suspend fun challengeRegister(pubB64: String, privKey: ByteArray, seed: ByteArray, device: String, usesPasskey: Boolean): AccountSession {
        Log.d(TAG, "register/start pubKey=$pubB64")
        val challenge = api.registerStart(RegisterStartBody())
        Log.d(TAG, "register/start challenge=${challenge.challenge}")
        val sig = CryptoUtils.signChallenge(challenge.challenge, privKey)
        val encDevice = CryptoUtils.encryptData(device, seed)
        Log.d(TAG, "register/complete sig=${sig.take(20)}… device(enc)=${encDevice.take(20)}…")
        val resp = api.registerComplete(RegisterCompleteBody(pubB64, ChallengePayload(challenge.challenge, sig), encDevice, ProfileBody()))
        Log.d(TAG, "register/complete resp token=${resp.token.take(20)}… session=${resp.session} user=${resp.user}")
        return AccountSession(resp.session.userId, resp.token, resp.user.nickname, device, usesPasskey).also { persist(it) }
    }

    private suspend fun persist(account: AccountSession) {
        ctx.accountStore.edit { prefs ->
            prefs[KEY_TOKEN]    = account.token
            prefs[KEY_USER_ID]  = account.userId
            prefs[KEY_NICKNAME] = account.nickname
            prefs[KEY_DEVICE]   = account.deviceName
            prefs[KEY_PASSKEY]  = account.usesPasskey
        }
        upsertSavedProfile(account)
    }

    private fun AccountSession.bearer() = "Bearer $token"
}
