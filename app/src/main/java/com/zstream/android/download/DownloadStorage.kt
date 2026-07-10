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
 * Filename of the recovery index written by [DownloadStorage.writeIndexJson] — exposed so scanners
 * that walk the ZStream folder (e.g. LocalMediaRepository's manually-added local-folder feature)
 * can exclude it. It's disguised as video/mp4 (see writeIndexJson's doc comment for why) which
 * would otherwise make it look like a bogus playable entry to any such scanner.
 */
const val DOWNLOAD_INDEX_DISPLAY_NAME = "zstream_index.mp4"

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

    /**
     * [langTag] e.g. "en", used to disambiguate multiple subtitle tracks in the same folder.
     * [providerLabel] e.g. "Wyzie" — prefixed onto the filename so the origin is visible at a
     * glance in a file browser, for tracks not sourced directly from the stream provider.
     */
    fun createSubtitleFile(target: DownloadTarget, langTag: String, extension: String, providerLabel: String? = null): DownloadFile {
        val prefix = providerLabel?.let { "[$it] " }.orEmpty()
        return createFile(target, "$prefix${target.baseFileName()}.$langTag", extension, mimeTypeForSubtitle(extension))
    }

    private fun createFile(target: DownloadTarget, baseName: String, extension: String, mimeType: String): DownloadFile {
        val relativeFolder = (listOf("ZStream") + target.folderSegments()).joinToString("/")
        val displayName = "$baseName.$extension"

        if (isScopedStorage) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relativeFolder/")
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

    /**
     * Deletes any MediaStore row under Downloads/ZStream still stuck IS_PENDING=1 — orphaned
     * ".pending-<timestamp>-name" stubs left behind by a download that crashed/errored before
     * this app's own cleanup ran (older code paths didn't clean up on failure at all). Scoped
     * strictly to our own ZStream subfolder so it can never touch the user's other downloads.
     * No-op on legacy storage (no IS_PENDING concept there). Returns the number of rows deleted.
     */
    fun sweepOrphanedPendingFiles(): Int {
        if (!isScopedStorage) return 0
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.IS_PENDING}=1 AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/ZStream/%")
        return context.contentResolver.delete(collection, selection, args)
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

    /**
     * Deletes a file by the `displayPath` stored on a DownloadEntity (relative path like
     * "ZStream/Title (Year)/Title (Year).mp4"), for cases where only that string survives (e.g.
     * after process death) rather than the original DownloadFile/Uri handle.
     */
    fun deleteByDisplayPath(displayPath: String) {
        if (!isScopedStorage) {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(root, displayPath).delete()
            return
        }
        val relativeFolder = displayPath.substringBeforeLast('/', "")
        val displayName = displayPath.substringAfterLast('/')
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(displayName, "${Environment.DIRECTORY_DOWNLOADS}/$relativeFolder/")
        context.contentResolver.delete(collection, selection, args)
    }

    /**
     * Resolves a stored `displayPath` (relative path like "ZStream/Title (Year)/Title (Year).mp4")
     * back to a URI ExoPlayer can actually open — a plain relative path string isn't playable on
     * its own since scoped storage requires going back through MediaStore to get a content:// Uri.
     */
    fun resolvePlayableUri(displayPath: String): Uri? {
        if (!isScopedStorage) {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(root, displayPath)
            return if (file.exists()) Uri.fromFile(file) else null
        }
        val relativeFolder = displayPath.substringBeforeLast('/', "")
        val displayName = displayPath.substringAfterLast('/')
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(displayName, "${Environment.DIRECTORY_DOWNLOADS}/$relativeFolder/")
        return context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            android.content.ContentUris.withAppendedId(collection, id)
        }
    }

    /**
     * Deletes a destination's containing folder (and any now-empty parent folders up to but not
     * including "ZStream/") once its file(s) are gone — MediaStore only removes the row/file it
     * knows about, never the on-disk directory that held it, so a cancelled/deleted download would
     * otherwise leave an empty "Title (Year)/" folder behind forever.
     */
    fun deleteEmptyFolder(displayPath: String) {
        val relativeFolder = displayPath.substringBeforeLast('/', "")
        if (relativeFolder.isBlank()) return
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val zstreamRoot = File(root, "ZStream")
        var dir: File? = File(root, relativeFolder)
        while (dir != null && dir != zstreamRoot && dir.path.startsWith(zstreamRoot.path)) {
            val entries = dir.listFiles()
            if (entries != null && entries.isNotEmpty()) break
            val parent = dir.parentFile
            dir.delete()
            dir = parent
        }
    }

    /** Bytes free / bytes total on the volume backing the Downloads folder. */
    fun freeSpaceInfo(): Pair<Long, Long> {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val stat = android.os.StatFs(root.absolutePath)
        val free = stat.availableBytes
        val total = stat.totalBytes
        return free to total
    }

    private fun mimeTypeForVideo(extension: String) = when (extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }

    private fun mimeTypeForSubtitle(extension: String) = when (extension.lowercase()) {
        "vtt" -> "text/vtt"
        else -> "application/x-subrip"
    }

    /**
     * Writes (overwriting any previous copy) a small recovery index of completed downloads to a
     * fixed path — "Downloads/ZStream/zstream_index.mp4" — so DownloadIndexSync can rebuild the
     * DownloadEntity rows for these files after an uninstall/reinstall wipes the app-private Room
     * database. Lives at the ZStream root, not nested per-title like video files.
     *
     * Named/typed as video/mp4 (the content is still plain JSON text) rather than application/json
     * on purpose: confirmed via a live MediaStore dump that uninstalling nulls out a row's
     * owner_package_name, and Android 13+'s granular storage permissions only grant cross-app
     * visibility for their own media type (READ_MEDIA_VIDEO covers any video file system-wide,
     * matching why the actual downloaded .mp4s stay resolvable after a reinstall) -- there is no
     * equivalent permission for arbitrary file types, so a real application/json row becomes
     * permanently unqueryable the moment its owning install is gone, regardless of any query logic
     * here. Riding along under READ_MEDIA_VIDEO (already requested and granted for playback) is
     * what actually makes this survive a reinstall, short of requesting MANAGE_EXTERNAL_STORAGE.
     */
    fun writeIndexJson(json: String) {
        if (!isScopedStorage) {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(root, INDEX_RELATIVE_FOLDER)
            folder.mkdirs()
            File(folder, INDEX_DISPLAY_NAME).writeText(json)
            return
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolveIndexUriForWrite(collection)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not open output stream for $INDEX_DISPLAY_NAME")
    }

    /**
     * Resolves the MediaStore row to write the index to, preferring a cached row id from a
     * previous write over re-querying — querying by displayName/relativePath after insert proved
     * unreliable in practice (kept creating "(1)", "(2)"... duplicates every write instead of
     * finding the row it had just inserted), so the row id is cached in SharedPreferences (stable
     * for the life of the row) and reused directly, bypassing that query entirely for repeat
     * writes within the same install.
     */
    private fun resolveIndexUriForWrite(collection: Uri): Uri {
        val prefs = context.getSharedPreferences(INDEX_PREFS_NAME, Context.MODE_PRIVATE)
        val cachedId = prefs.getLong(INDEX_PREF_ROW_ID, -1L)
        if (cachedId != -1L) {
            val cachedUri = android.content.ContentUris.withAppendedId(collection, cachedId)
            val stillExists = context.contentResolver.query(
                cachedUri, arrayOf(MediaStore.MediaColumns._ID), null, null, null,
            )?.use { it.moveToFirst() } ?: false
            if (stillExists) return cachedUri
        }
        val found = queryIndexUri(collection)
        if (found != null) {
            prefs.edit().putLong(INDEX_PREF_ROW_ID, android.content.ContentUris.parseId(found)).apply()
            return found
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, INDEX_DISPLAY_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$INDEX_RELATIVE_FOLDER/")
        }
        val inserted = context.contentResolver.insert(collection, values)
            ?: error("MediaStore insert failed for $INDEX_DISPLAY_NAME")
        prefs.edit().putLong(INDEX_PREF_ROW_ID, android.content.ContentUris.parseId(inserted)).apply()
        return inserted
    }

    /** Reads back the recovery index written by [writeIndexJson]. Null if none exists yet. */
    fun readIndexJson(): String? {
        if (!isScopedStorage) {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(File(root, INDEX_RELATIVE_FOLDER), INDEX_DISPLAY_NAME)
            return if (file.exists()) file.readText() else null
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = queryIndexUri(collection) ?: return null
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun queryIndexUri(collection: Uri): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("zstream_index%")
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        return context.contentResolver.query(collection, projection, selection, args, sortOrder)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            android.content.ContentUris.withAppendedId(collection, id)
        }
    }

    private companion object {
        const val INDEX_RELATIVE_FOLDER = "ZStream"
        const val INDEX_DISPLAY_NAME = DOWNLOAD_INDEX_DISPLAY_NAME
        const val INDEX_PREFS_NAME = "download_storage_index"
        const val INDEX_PREF_ROW_ID = "index_row_id"
    }
}
