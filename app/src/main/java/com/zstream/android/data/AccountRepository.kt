package com.zstream.android.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zstream.android.data.CryptoUtils.toBase64Url
import com.zstream.android.data.remote.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

private const val TAG = "AccountRepo"
private val Context.accountStore by preferencesDataStore("account")

data class AccountSession(val userId: String, val token: String, val nickname: String, val deviceName: String = "", val usesPasskey: Boolean = false)

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

    suspend fun loginWithPasskey(deviceName: String = "Android"): AccountSession {
        val credId = CryptoUtils.authenticatePasskey(ctx)
        val keys   = CryptoUtils.keysFromSeed(CryptoUtils.pbkdf2(credId))
        return challengeLogin(keys.publicKey.toBase64Url(), keys.privateKey, keys.seed, deviceName, true)
    }

    /** Login using a raw 32-byte seed (e.g. received via TV sync from a passkey account). */
    suspend fun loginWithSeed(seedBase64Url: String, deviceName: String): AccountSession {
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        val seed = kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT).decode(seedBase64Url)
        val keys = CryptoUtils.keysFromSeed(seed)
        return challengeLogin(keys.publicKey.toBase64Url(), keys.privateKey, keys.seed, deviceName, true)
    }

    suspend fun registerWithPasskey(userName: String, deviceName: String = "Android"): AccountSession {
        val credId = CryptoUtils.createPasskey(ctx, userName)
        val keys   = CryptoUtils.keysFromSeed(CryptoUtils.pbkdf2(credId))
        return challengeRegister(keys.publicKey.toBase64Url(), keys.privateKey, keys.seed, deviceName, true)
    }

    suspend fun logout() = ctx.accountStore.edit { it.clear() }

    /**
     * Get a persistent guest ID for anonymous watch party participation.
     */
    suspend fun getOrCreateGuestId(): String {
        val prefs = ctx.accountStore.data.first()
        val existing = prefs[KEY_GUEST_ID]
        if (existing != null) return existing

        val newId = java.util.UUID.randomUUID().toString()
        ctx.accountStore.edit { it[KEY_GUEST_ID] = newId }
        return newId
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

    private suspend fun challengeLogin(pubB64: String, privKey: ByteArray, seed: ByteArray, device: String, usesPasskey: Boolean): AccountSession {
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
        return AccountSession(userId, resp.token, resp.user?.nickname ?: "", device, usesPasskey).also { persist(it) }
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

    private suspend fun persist(account: AccountSession) = ctx.accountStore.edit { prefs ->
        prefs[KEY_TOKEN]    = account.token
        prefs[KEY_USER_ID]  = account.userId
        prefs[KEY_NICKNAME] = account.nickname
        prefs[KEY_DEVICE]   = account.deviceName
        prefs[KEY_PASSKEY]  = account.usesPasskey
    }

    private fun AccountSession.bearer() = "Bearer $token"
}
