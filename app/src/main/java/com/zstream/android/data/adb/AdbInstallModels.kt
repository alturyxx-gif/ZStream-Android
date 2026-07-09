package com.zstream.android.data.adb

import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CancellationException

enum class AdbFailureKind {
    DISCOVERY,
    PAIRING,
    PAIRING_REQUIRED,
    CONNECTION,
    DOWNLOAD,
    INSTALL,
    WRONG_DEVICE,
    CANCELLED,
    UNKNOWN,
}

class AdbOperationException(
    val kind: AdbFailureKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class SavedTv(
    val host: String,
    val model: String,
    val legacyPort: Int? = null,
    val connectPort: Int? = legacyPort,
    val pairingPort: Int? = null,
    val nickname: String = model,
    val id: String = if (legacyPort == null) "wireless:$host" else "legacy:$host:$legacyPort",
)

internal fun mergeSavedTv(devices: List<SavedTv>, device: SavedTv): List<SavedTv> {
    val previous = devices.firstOrNull { it.id == device.id }
    val merged = if (previous != null && device.nickname == device.model) {
        device.copy(nickname = previous.nickname)
    } else {
        device
    }
    return devices.filterNot { it.id == device.id } + merged
}

sealed interface InstallProgress {
    data object Connecting : InstallProgress
    data class Downloading(val bytes: Long, val totalBytes: Long) : InstallProgress
    data class Transferring(val bytes: Long, val totalBytes: Long) : InstallProgress
    data object Installing : InstallProgress
}

data class InstallResult(val model: String, val packageManagerOutput: String)

internal fun validateApkUrl(url: String): String {
    val parsed = try {
        URI(url.trim())
    } catch (e: Exception) {
        throw AdbOperationException(AdbFailureKind.DOWNLOAD, "Invalid APK URL", e)
    }
    if (parsed.scheme != "https" || parsed.host.isNullOrBlank()) {
        throw AdbOperationException(AdbFailureKind.DOWNLOAD, "APK download URL must be HTTPS")
    }
    return parsed.toString()
}

/**
 * GitHub release assets carry a `digest` field shaped like "sha256:<hex>". Verifies the
 * downloaded APK matches it before it's ever pushed to `cmd package install` on the TV --
 * without this, a MITM'd or substituted download would be installed with no integrity check.
 * Assets without a digest (older uploads) skip verification rather than blocking install.
 */
internal fun verifyApkDigest(file: File, expectedDigest: String?) {
    if (expectedDigest.isNullOrBlank()) return
    val expectedHex = expectedDigest.substringAfter("sha256:", "").lowercase()
    if (expectedHex.isBlank()) return
    val actualHex = MessageDigest.getInstance("SHA-256").let { md ->
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
    if (!actualHex.equals(expectedHex, ignoreCase = true)) {
        throw AdbOperationException(AdbFailureKind.DOWNLOAD, "Downloaded APK failed checksum verification")
    }
}

internal fun packageManagerSucceeded(output: String): Boolean =
    output.lineSequence().any { it.trim() == "Success" }

internal fun connectionFailure(cause: Throwable): AdbOperationException = when (cause) {
    is AdbAuthenticationFailedException, is AdbPairingRequiredException -> AdbOperationException(
        AdbFailureKind.PAIRING_REQUIRED,
        "TV authorization was revoked. Pair again.",
        cause,
    )
    else -> AdbOperationException(AdbFailureKind.CONNECTION, "Could not connect to the TV.", cause)
}

internal fun copyCancellable(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long,
    isCancelled: () -> Boolean,
    onProgress: (Long, Long) -> Unit,
): Long {
    val buffer = ByteArray(1024 * 1024)
    var copied = 0L
    while (true) {
        if (isCancelled()) throw CancellationException("Transfer cancelled")
        val read = input.read(buffer)
        if (read < 0) return copied
        output.write(buffer, 0, read)
        copied += read
        onProgress(copied, totalBytes)
    }
}

internal fun orderDiscoveredDevices(
    devices: List<DiscoveredAdbEndpoints>,
    savedHost: String?,
): List<DiscoveredAdbEndpoints> = devices.sortedWith(
    compareByDescending<DiscoveredAdbEndpoints> { it.host == savedHost }
        .thenByDescending { it.pairingPort != null }
        .thenByDescending { it.host.startsWith("192.168.0.") },
)
