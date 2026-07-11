package com.zstream.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class CryptoUtilsTest {
    @Test
    fun pbkdf2MatchesTheStandardSha256Derivation() {
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("892014".toCharArray(), "tvsync:example".toByteArray(), 2048, 256))
            .encoded

        assertArrayEquals(expected, CryptoUtils.pbkdf2("892014", "tvsync:example"))
    }

    @Test
    fun mnemonicFromEntropy_matchesOfficialBip39Vector() {
        val wordList = Files.readAllLines(
            Paths.get(System.getProperty("user.dir"), "src", "main", "assets", "bip39_english.txt")
        ).filter { it.isNotBlank() }

        assertEquals(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
            CryptoUtils.mnemonicFromEntropy(ByteArray(16), wordList),
        )
    }
}
