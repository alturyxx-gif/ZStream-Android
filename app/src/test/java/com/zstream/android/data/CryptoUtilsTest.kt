package com.zstream.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class CryptoUtilsTest {
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
