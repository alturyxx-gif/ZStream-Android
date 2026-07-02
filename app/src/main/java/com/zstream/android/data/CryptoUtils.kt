package com.zstream.android.data

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Ed25519Keys(val privateKey: ByteArray, val publicKey: ByteArray, val seed: ByteArray)

object CryptoUtils {
    @Volatile private var bip39EnglishWords: List<String>? = null

    /** PBKDF2-SHA256, 2048 iterations, 32 bytes — mirrors p-stream's seedFromMnemonic */
    fun pbkdf2(passphrase: String, salt: String = "mnemonic"): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt.toByteArray(), 2048, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun keysFromSeed(seed: ByteArray): Ed25519Keys {
        require(seed.size == 32)
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        return Ed25519Keys(priv.encoded, priv.generatePublicKey().encoded, seed)
    }

    fun keysFromPassphrase(passphrase: String): Ed25519Keys = keysFromSeed(pbkdf2(passphrase))

    fun generateMnemonic(context: Context): String {
        val entropy = ByteArray(16).also(SecureRandom()::nextBytes)
        return mnemonicFromEntropy(entropy, bip39EnglishWords(context))
    }

    internal fun mnemonicFromEntropy(entropy: ByteArray, wordList: List<String>): String {
        require(entropy.size == 16) { "Expected 128-bit entropy for a 12-word mnemonic" }
        require(wordList.size == 2048) { "Expected full BIP39 English wordlist" }

        val entropyBits = entropy.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(2).padStart(8, '0')
        }
        val checksumLength = entropy.size / 4
        val checksumBits = (MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xFF)
            .toString(2)
            .padStart(8, '0')
            .take(checksumLength)

        return (entropyBits + checksumBits)
            .chunked(11)
            .joinToString(" ") { wordList[it.toInt(2)] }
    }

    private fun bip39EnglishWords(context: Context): List<String> {
        bip39EnglishWords?.let { return it }
        synchronized(this) {
            bip39EnglishWords?.let { return it }
            val words = context.assets.open("bip39_english.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.toList()
            }
            require(words.size == 2048) { "Invalid BIP39 English wordlist asset" }
            bip39EnglishWords = words
            return words
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun signChallenge(challenge: String, privateKeyBytes: ByteArray): String {
        val priv = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
        val signer = Ed25519Signer().apply { init(true, priv) }
        val msg = challenge.toByteArray(Charsets.UTF_8)
        signer.update(msg, 0, msg.size)
        return Base64.UrlSafe.encode(signer.generateSignature()).trimEnd('=')
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun ByteArray.toBase64Url(): String = Base64.UrlSafe.encode(this).trimEnd('=')

    //  AES-GCM encryption (device name) 
    // Mirrors p-stream encryptData(data, secret) → "base64(iv).base64(ct).base64(tag)"

    fun encryptData(plaintext: String, secret: ByteArray): String {
        require(secret.size == 32) { "secret must be 32 bytes" }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(secret, "AES")
        val paramSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, paramSpec)
        val ctWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // GCM appends 16-byte tag to ciphertext
        val ct = ctWithTag.copyOf(ctWithTag.size - 16)
        val tag = ctWithTag.copyOfRange(ctWithTag.size - 16, ctWithTag.size)
        @OptIn(ExperimentalEncodingApi::class)
        return "${Base64.Default.encode(iv)}.${Base64.Default.encode(ct)}.${Base64.Default.encode(tag)}"
    }

    fun decryptData(ciphertext: String, secret: ByteArray): String {
        require(secret.size == 32) { "secret must be 32 bytes" }
        val parts = ciphertext.split(".")
        require(parts.size == 3) { "invalid ciphertext" }
        @OptIn(ExperimentalEncodingApi::class)
        val iv = Base64.Default.decode(parts[0])
        @OptIn(ExperimentalEncodingApi::class)
        val ct = Base64.Default.decode(parts[1])
        @OptIn(ExperimentalEncodingApi::class)
        val tag = Base64.Default.decode(parts[2])
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(secret, "AES")
        val paramSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, paramSpec)
        return cipher.doFinal(ct + tag).toString(Charsets.UTF_8)
    }

    //  Passkey helpers 

    @OptIn(ExperimentalEncodingApi::class)
    private fun randomBase64Url(bytes: Int = 32): String {
        val buf = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.UrlSafe.encode(buf).trimEnd('=')
    }

    /** Register a new passkey, returns credential ID. */
    suspend fun createPasskey(ctx: Context, userName: String): String {
        val requestJson = JSONObject().apply {
            put("challenge", randomBase64Url())
            put("rp", JSONObject().put("name", "Z-Stream").put("id", com.zstream.android.Urls.PASSKEY_RP_ID))
            put("user", JSONObject()
                .put("id", randomBase64Url(8))
                .put("name", userName)
                .put("displayName", userName))
            put("pubKeyCredParams", JSONArray().apply {
                put(JSONObject().put("type", "public-key").put("alg", -7))
                put(JSONObject().put("type", "public-key").put("alg", -257))
            })
            put("authenticatorSelection", JSONObject()
                .put("userVerification", "preferred")
                .put("residentKey", "preferred"))
            put("timeout", 60000)
            put("attestation", "none")
        }.toString()

        val result = CredentialManager.create(ctx)
            .createCredential(ctx, CreatePublicKeyCredentialRequest(requestJson))
        val responseJson = result.data.getString(
            "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
        ) ?: error("No registration response JSON")
        return JSONObject(responseJson).getString("id")
    }

    /** Authenticate with an existing passkey, returns credential ID. */
    suspend fun authenticatePasskey(ctx: Context): String {
        val requestJson = JSONObject().apply {
            put("challenge", randomBase64Url())
            put("timeout", 60000)
            put("userVerification", "preferred")
            put("rpId", com.zstream.android.Urls.PASSKEY_RP_ID)
        }.toString()

        val result = CredentialManager.create(ctx)
            .getCredential(ctx, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson))))
        val cred = result.credential as PublicKeyCredential
        return JSONObject(cred.authenticationResponseJson).getString("id")
    }
}
