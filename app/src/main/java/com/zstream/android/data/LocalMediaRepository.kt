package com.zstream.android.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.zstream.android.data.local.dao.LocalLibraryDao
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private fun scan(treeUri: Uri, folderId: Long): List<LocalMediaEntity> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = displayName(treeUri)
        val out = mutableListOf<LocalMediaEntity>()
        scanChildren(treeUri, rootId, rootName, folderId, out)
        return out
    }

    private fun scanChildren(
        treeUri: Uri,
        parentDocumentId: String,
        relativeParent: String,
        folderId: Long,
        out: MutableList<LocalMediaEntity>,
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
                    scanChildren(treeUri, documentId, relativePath, folderId, out)
                } else if (isVideo(name, mime)) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val metadata = metadata(uri)
                    val guess = LocalMediaGrouper.infer(relativePath, metadata.title)
                    out += LocalMediaEntity(
                        folderId = folderId,
                        documentUri = uri.toString(),
                        displayName = name,
                        relativePath = relativePath,
                        size = if (cursor.isNull(sizeCol)) null else cursor.getLong(sizeCol),
                        durationMs = metadata.durationMs,
                        modifiedAt = if (cursor.isNull(modifiedCol)) null else cursor.getLong(modifiedCol),
                        groupTitle = guess.groupTitle,
                        mediaKind = guess.mediaKind,
                        season = guess.season,
                        episode = guess.episode,
                    )
                }
            }
        }
    }

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
        if (mime?.startsWith("video/") == true) return true
        return name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")
    }

    private data class Metadata(val title: String? = null, val durationMs: Long? = null)
}
