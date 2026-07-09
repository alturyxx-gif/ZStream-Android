package com.zstream.android.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A file the download pipeline is writing to — either a MediaStore "Downloads" entry (API 29+,
 * scoped storage) or a plain File under the legacy public Downloads directory (API 24-28, needs
 * WRITE_EXTERNAL_STORAGE, declared maxSdkVersion="28" in the manifest since it's a no-op/denied
 * on 29+ anyway).
 */
sealed class DownloadFile {
    abstract val displayPath: String

    data class MediaStoreFile(val uri: Uri, override val displayPath: String) : DownloadFile()
    data class LegacyFile(val file: File) : DownloadFile() {
        override val displayPath: String get() = file.absolutePath
    }
}

/**
 * Creates destination files under Downloads/ZStream/<folderSegments>/ following the layout:
 *   Movie:   ZStream/{Title (Year)}/{Title (Year)}.mp4 (+ subtitles alongside)
 *   Episode: ZStream/{Show}/Season NN/S NNENN - Title/S NNENN - Title.mp4 (+ subtitles alongside)
 */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val isScopedStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** [extension] without a leading dot, e.g. "mp4", "srt". */
    fun createVideoFile(target: DownloadTarget, extension: String): DownloadFile =
        createFile(target, target.baseFileName(), extension, mimeTypeForVideo(extension))

    /** [langTag] e.g. "en", used to disambiguate multiple subtitle tracks in the same folder. */
    fun createSubtitleFile(target: DownloadTarget, langTag: String, extension: String): DownloadFile =
        createFile(target, "${target.baseFileName()}.$langTag", extension, mimeTypeForSubtitle(extension))

    private fun createFile(target: DownloadTarget, baseName: String, extension: String, mimeType: String): DownloadFile {
        val relativeFolder = (listOf("ZStream") + target.folderSegments()).joinToString("/")
        val displayName = "$baseName.$extension"

        if (isScopedStorage) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relativeFolder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: error("MediaStore insert failed for $displayName")
            return DownloadFile.MediaStoreFile(uri, "$relativeFolder/$displayName")
        }

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(root, relativeFolder)
        folder.mkdirs()
        return DownloadFile.LegacyFile(File(folder, displayName))
    }

    /** Opens the file for writing. Caller must close the stream. */
    fun openOutputStream(file: DownloadFile): OutputStream = when (file) {
        is DownloadFile.MediaStoreFile -> context.contentResolver.openOutputStream(file.uri)
            ?: error("Could not open output stream for ${file.displayPath}")
        is DownloadFile.LegacyFile -> file.file.outputStream()
    }

    /**
     * Opens a ParcelFileDescriptor for MediaMuxer's FileDescriptor constructor (use `.fileDescriptor`).
     * Caller must call `.close()` on the returned ParcelFileDescriptor when the muxer is done —
     * closing MediaMuxer alone does not release this handle.
     */
    fun openParcelFileDescriptorForMuxer(file: DownloadFile): android.os.ParcelFileDescriptor = when (file) {
        is DownloadFile.MediaStoreFile -> context.contentResolver.openFileDescriptor(file.uri, "w")
            ?: error("Could not open file descriptor for ${file.displayPath}")
        is DownloadFile.LegacyFile -> android.os.ParcelFileDescriptor.open(
            file.file,
            android.os.ParcelFileDescriptor.MODE_READ_WRITE or android.os.ParcelFileDescriptor.MODE_CREATE or android.os.ParcelFileDescriptor.MODE_TRUNCATE,
        )
    }

    /** Must be called after writing finishes so MediaStore reveals the file (no-op on legacy storage). */
    fun finalize(file: DownloadFile) {
        if (file is DownloadFile.MediaStoreFile) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(file.uri, values, null, null)
        }
    }

    /** Final file size in bytes, once writing/finalize is done. Null if it can't be determined. */
    fun fileSize(file: DownloadFile): Long? = when (file) {
        is DownloadFile.MediaStoreFile -> context.contentResolver.query(
            file.uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
        is DownloadFile.LegacyFile -> file.file.length().takeIf { it > 0 }
    }

    /** Deletes a destination, e.g. on download cancel/failure cleanup. */
    fun delete(file: DownloadFile) {
        when (file) {
            is DownloadFile.MediaStoreFile -> context.contentResolver.delete(file.uri, null, null)
            is DownloadFile.LegacyFile -> file.file.delete()
        }
    }

    private fun mimeTypeForVideo(extension: String) = when (extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }

    private fun mimeTypeForSubtitle(extension: String) = when (extension.lowercase()) {
        "vtt" -> "text/vtt"
        else -> "application/x-subrip"
    }
}
