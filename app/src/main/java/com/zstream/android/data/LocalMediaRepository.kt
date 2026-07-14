package com.zstream.android.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.dao.LocalLibraryDao
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.DownloadStatus
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import com.zstream.android.download.DownloadStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LocalLibraryDao,
    private val downloadDao: DownloadDao,
    private val tmdbRepository: TmdbRepository,
    private val downloadStorage: DownloadStorage,
) {
    val folders: Flow<List<LocalLibraryFolderEntity>> = dao.observeFolders()
    val media: Flow<List<LocalMediaEntity>> = dao.observeMedia()
    private val _scanningFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val scanningFolderIds: Flow<Set<Long>> = _scanningFolderIds.asStateFlow()

    suspend fun getMedia(id: Long): LocalMediaEntity? = dao.getMedia(id)

    suspend fun siblingSubtitles(media: LocalMediaEntity): List<Pair<String, Uri>> = withContext(Dispatchers.IO) {
        val folder = dao.getFolder(media.folderId) ?: return@withContext emptyList()
        val treeUri = Uri.parse(folder.treeUri)
        val videoUri = Uri.parse(media.documentUri)
        val documentId = DocumentsContract.getDocumentId(videoUri)
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentId.isBlank()) return@withContext emptyList()
        val baseName = media.displayName.substringBeforeLast('.', media.displayName)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        val subtitles = mutableListOf<Pair<String, Uri>>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if ((ext == "srt" || ext == "vtt") && name.substringBeforeLast('.', name).startsWith(baseName)) {
                    subtitles += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                }
            }
        }
        subtitles
    }

    suspend fun addFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val folder = LocalLibraryFolderEntity(treeUri = treeUri.toString(), displayName = displayName(treeUri))
        val id = dao.insertFolder(folder)
        scanFolder(folder.copy(id = id))
    }

    suspend fun removeFolder(folder: LocalLibraryFolderEntity) = withContext(Dispatchers.IO) {
        dao.deleteFolder(folder)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun scanFolder(folder: LocalLibraryFolderEntity) = withContext(Dispatchers.IO) {
        _scanningFolderIds.update { it + folder.id }
        try {
            val scanned = runCatching { scan(Uri.parse(folder.treeUri), folder.id) }
            val now = System.currentTimeMillis()
            if (scanned.isSuccess) {
                dao.replaceMediaForFolder(folder.id, scanned.getOrThrow())
                dao.updateFolder(folder.copy(lastScanAt = now, lastScanError = null))
            } else {
                dao.updateFolder(folder.copy(lastScanAt = now, lastScanError = scanned.exceptionOrNull()?.message ?: "Scan failed"))
            }
        } finally {
            _scanningFolderIds.update { it - folder.id }
        }
    }

    private suspend fun scan(treeUri: Uri, folderId: Long): List<LocalMediaEntity> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = displayName(treeUri)
        val candidates = mutableListOf<ScanCandidate>()
        val previous = dao.getMediaForFolder(folderId)
            .associateBy { "${it.documentUri}:${it.size}:${it.modifiedAt}" }
        val downloads = downloadDao.getAllSync().filter { it.status == DownloadStatus.DONE }.map { it.withFingerprint() }
        val downloadFingerprints = downloads.mapNotNull { it.contentFingerprint }.toSet()
        scanChildren(treeUri, rootId, rootName, candidates)
        return candidates.mapNotNull { candidate -> candidate.toEntity(folderId, previous[candidate.previousKey], downloads, downloadFingerprints) }
    }

    private fun scanChildren(
        treeUri: Uri,
        parentDocumentId: String,
        relativeParent: String,
        out: MutableList<ScanCandidate>,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val mime = cursor.getString(mimeCol)
                val relativePath = "$relativeParent/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanChildren(treeUri, documentId, relativePath, out)
                } else if (isVideo(name, mime)) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val metadata = metadata(uri)
                    val size = if (cursor.isNull(sizeCol)) null else cursor.getLong(sizeCol)
                    val modifiedAt = if (cursor.isNull(modifiedCol)) null else cursor.getLong(modifiedCol)
                    out += ScanCandidate(uri, name, relativePath, size, metadata.durationMs, modifiedAt, metadata.title)
                }
            }
        }
    }

    private suspend fun ScanCandidate.toEntity(
        folderId: Long,
        previous: LocalMediaEntity?,
        downloads: List<DownloadEntity>,
        downloadFingerprints: Set<String>,
    ): LocalMediaEntity? {
        val guess = LocalMediaGrouper.infer(relativePath, metadataTitle)
        val download = matchDownload(this, guess, downloads)
        if (download != null) return null
        val fingerprint = VideoFingerprint.compute(context, uri, size, durationMs)
        if (fingerprint != null && fingerprint in downloadFingerprints) return null
        val base = when {
            previous != null && previous.matchSource != "uncategorized" -> MatchedMedia.from(previous)
            else -> MatchedMedia.from(guess)
        }.withTmdbMatch()
        val thumbnail = base.posterPath?.let { null } ?: previous?.thumbnailPath ?: thumbnailFor(uri, durationMs, size, modifiedAt)
        return LocalMediaEntity(
            folderId = folderId,
            documentUri = uri.toString(),
            displayName = displayName,
            relativePath = relativePath,
            size = size,
            durationMs = durationMs,
            modifiedAt = modifiedAt,
            groupTitle = base.groupTitle,
            mediaKind = base.mediaKind,
            season = base.season,
            episode = base.episode,
            groupKey = base.groupKey,
            matchSource = base.matchSource,
            tmdbId = base.tmdbId,
            tmdbType = base.tmdbType,
            posterPath = base.posterPath,
            thumbnailPath = thumbnail,
            metadataTitle = metadataTitle,
            fingerprint = fingerprint,
        )
    }

    private suspend fun DownloadEntity.withFingerprint(): DownloadEntity {
        if (contentFingerprint != null) return this
        val path = filePath ?: return this
        val uri = downloadStorage.resolvePlayableUri(path, storageTreeUri?.let(Uri::parse)) ?: return this
        val fingerprint = VideoFingerprint.compute(context, uri, fileSize(uri), null) ?: return this
        val updated = copy(contentFingerprint = fingerprint)
        downloadDao.update(updated)
        return updated
    }

    private fun fileSize(uri: Uri): Long? =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.takeIf { size -> size > 0 } }

    private suspend fun MatchedMedia.withTmdbMatch(): MatchedMedia {
        if (tmdbId != null || groupTitle == LocalMediaGrouper.UNCATEGORIZED) return this
        val expected = if (mediaKind == "show" || season != null || episode != null) "tv" else "movie"
        val media = runCatching { tmdbRepository.search(groupTitle).let { LocalTmdbMatcher.best(groupTitle, expected, it) } }.getOrNull()
            ?: return this
        val type = if (media.type == "tv") "show" else "movie"
        return copy(
            groupTitle = media.displayTitle.ifBlank { groupTitle },
            mediaKind = type,
            groupKey = "tmdb:${media.type}:${media.id}",
            matchSource = if (matchSource == "uncategorized") "tmdb" else "$matchSource+tmdb",
            tmdbId = media.id.toString(),
            tmdbType = media.type,
            posterPath = media.posterPath,
        )
    }

    private fun matchDownload(candidate: ScanCandidate, guess: LocalMediaGuess, downloads: List<DownloadEntity>): DownloadEntity? {
        val fileName = candidate.displayName.lowercase()
        val relativePath = candidate.relativePath.normalizedPath()
        return downloads.firstOrNull { download ->
            val storedPath = download.filePath?.normalizedPath().orEmpty()
            if (storedPath.isBlank()) return@firstOrNull false
            val storedTail = storedPath.substringAfter("download/", storedPath).removePrefix("/")
            val exactPathMatch = relativePath.endsWith(storedTail)
            val sameNamedEpisodeInExpectedShow = storedPath.substringAfterLast('/') == fileName &&
                relativePath.contains(download.title.normalizedPath()) &&
                (download.type != "show" || ((guess.season == null || download.season == guess.season) &&
                    (guess.episode == null || download.episode == guess.episode)))
            val fileMatches = exactPathMatch || sameNamedEpisodeInExpectedShow
            if (!fileMatches) return@firstOrNull false
            true
        }
    }

    private fun String.normalizedPath(): String = lowercase()
        .replace('\\', '/')
        .replace(Regex("""\s+"""), " ")

    private fun thumbnailFor(uri: Uri, durationMs: Long?, size: Long?, modifiedAt: Long?): String? {
        val key = sha256("${uri}|$size|$modifiedAt")
        val file = File(context.cacheDir, "local_thumbs/$key.jpg")
        if (file.exists()) return file.absolutePath
        file.parentFile?.mkdirs()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val atUs = durationMs?.let { (it / 2).coerceAtLeast(1) * 1000 } ?: -1
            val bitmap = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            file.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun displayName(uri: Uri): String {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "Folder"
        }
        return uri.lastPathSegment ?: "Folder"
    }

    private fun metadata(uri: Uri): Metadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            )
        } catch (_: Throwable) {
            Metadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isVideo(name: String, mime: String?): Boolean {
        if (name.startsWith(".") || name.contains(".pending-", ignoreCase = true)) return false
        if (mime?.startsWith("video/") == true) return true
        return name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")
    }

    private data class Metadata(val title: String? = null, val durationMs: Long? = null)

    private data class ScanCandidate(
        val uri: Uri,
        val displayName: String,
        val relativePath: String,
        val size: Long?,
        val durationMs: Long?,
        val modifiedAt: Long?,
        val metadataTitle: String?,
    ) {
        val previousKey: String get() = "${uri}:${size}:${modifiedAt}"
    }

    private data class MatchedMedia(
        val groupTitle: String,
        val mediaKind: String,
        val season: Int?,
        val episode: Int?,
        val groupKey: String,
        val matchSource: String,
        val tmdbId: String? = null,
        val tmdbType: String? = null,
        val posterPath: String? = null,
    ) {
        companion object {
            fun from(guess: LocalMediaGuess) = MatchedMedia(
                groupTitle = guess.groupTitle,
                mediaKind = guess.mediaKind,
                season = guess.season,
                episode = guess.episode,
                groupKey = guess.groupKey,
                matchSource = guess.matchSource,
            )

            fun from(media: LocalMediaEntity) = MatchedMedia(
                groupTitle = media.groupTitle,
                mediaKind = media.mediaKind,
                season = media.season,
                episode = media.episode,
                groupKey = media.groupKey,
                matchSource = media.matchSource,
                tmdbId = media.tmdbId,
                tmdbType = media.tmdbType,
                posterPath = media.posterPath,
            )
        }
    }
}
