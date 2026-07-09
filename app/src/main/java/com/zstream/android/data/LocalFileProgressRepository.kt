package com.zstream.android.data

import com.zstream.android.data.local.dao.LocalFileProgressDao
import com.zstream.android.data.local.entity.LocalFileProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

fun buildLocalFileProgressId(fingerprint: String?, folderId: Long, documentUri: String): String =
    fingerprint?.let { "fp_$it" } ?: "uri_${folderId}_${documentUri.hashCode()}"

@Singleton
class LocalFileProgressRepository @Inject constructor(
    private val dao: LocalFileProgressDao,
) {
    suspend fun get(id: String): LocalFileProgressEntity? = dao.get(id)

    suspend fun update(
        id: String,
        title: String,
        watched: Int,
        duration: Int,
        posterPath: String?,
        thumbnailPath: String?,
    ) {
        dao.insert(
            LocalFileProgressEntity(
                id = id,
                title = title,
                watched = watched,
                duration = duration,
                posterPath = posterPath,
                thumbnailPath = thumbnailPath,
            )
        )
    }
}
