package com.zstream.android.data.adb

import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
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

data class SavedTv(val host: String, val model: String, val legacyPort: Int? = null)

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
    if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) {
        throw AdbOperationException(AdbFailureKind.DOWNLOAD, "Invalid APK URL")
    }
    return parsed.toString()
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
    val buffer = ByteArray(64 * 1024)
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
