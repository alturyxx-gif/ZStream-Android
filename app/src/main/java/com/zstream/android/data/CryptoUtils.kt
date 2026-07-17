package com.zstream.android.data

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Ed25519Keys(val privateKey: ByteArray, val publicKey: ByteArray, val seed: ByteArray)

object CryptoUtils {
    @Volatile private var bip39EnglishWords: List<String>? = null

    /** PBKDF2-SHA256, 2048 iterations, 32 bytes — mirrors p-stream's seedFromMnemonic */
    fun pbkdf2(passphrase: String, salt: String = "mnemonic"): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(passphrase.toByteArray(Charsets.UTF_8), salt.toByteArray(Charsets.UTF_8), 2048)
        return (generator.generateDerivedParameters(256) as KeyParameter).key
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

    /**
     * Runs the WebAuthn *registration* ceremony against server-issued [optionsJson] (the
     * `options` object from POST /auth/passkey/register/options, serialized as-is) and returns the
     * authenticator's attestation response JSON, ready to post to /register/verify.
     *
     * The challenge and RP id must come from the server -- the app no longer mints its own -- so
     * that @simplewebauthn's verify step accepts them.
     */
    suspend fun passkeyRegisterResponse(ctx: Context, optionsJson: String): String {
        val result = CredentialManager.create(ctx)
            .createCredential(ctx, CreatePublicKeyCredentialRequest(optionsJson))
        return result.data.getString(
            "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
        ) ?: error("No registration response JSON")
    }

    /**
     * Runs the WebAuthn *authentication* ceremony against server-issued [optionsJson] (the
     * `options` object from POST /auth/passkey/login/options) and returns the authenticator's
     * assertion response JSON, ready to post to /login/verify.
     */
    suspend fun passkeyAuthenticationResponse(ctx: Context, optionsJson: String): String {
        val result = CredentialManager.create(ctx)
            .getCredential(ctx, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(optionsJson))))
        return (result.credential as PublicKeyCredential).authenticationResponseJson
    }
}
