package com.zstream.android.data.adb

import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbInstallModelsTest {
    @Test
    fun apkUrlValidationRejectsBadUrls() {
        assertEquals("https://example.com/app.apk", validateApkUrl(" https://example.com/app.apk "))
        assertThrows(AdbOperationException::class.java) { validateApkUrl("not a url") }
        assertThrows(AdbOperationException::class.java) { validateApkUrl("file:///tmp/app.apk") }
    }

    @Test
    fun packageManagerResultRequiresSuccessLine() {
        assertTrue(packageManagerSucceeded("Success\n"))
        assertFalse(packageManagerSucceeded("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"))
    }

    @Test
    fun savedDeviceWinsMultipleDeviceSelection() {
        val other = DiscoveredAdbEndpoints("192.168.0.10", 1111, 2222)
        val saved = DiscoveredAdbEndpoints("192.168.1.20", null, 3333)
        assertEquals(saved, orderDiscoveredDevices(listOf(other, saved), saved.host).first())
    }

    @Test
    fun savedLegacyTvKeepsItsFixedAdbPort() {
        assertEquals(5555, SavedTv("192.168.0.20", "TV", 5555).legacyPort)
    }

    @Test
    fun revokedAuthorizationRequestsPairing() {
        assertEquals(AdbFailureKind.PAIRING_REQUIRED, connectionFailure(AdbAuthenticationFailedException()).kind)
    }

    @Test
    fun interruptedTransferStopsBeforeAllBytesAreWritten() {
        val input = ByteArrayInputStream(ByteArray(2 * 1024 * 1024))
        val output = ByteArrayOutputStream()
        var cancelled = false
        assertThrows(CancellationException::class.java) {
            copyCancellable(input, output, input.available().toLong(), { cancelled }) { copied, _ ->
                if (copied >= 1024 * 1024) cancelled = true
            }
        }
        assertEquals(1024 * 1024, output.size())
    }
}
