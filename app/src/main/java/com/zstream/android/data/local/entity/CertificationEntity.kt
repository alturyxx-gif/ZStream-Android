package com.zstream.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached TMDB certification lookup, keyed by "movie_{id}" / "tv_{id}". Empty [certification] means "checked, none found" (treated as unrated). */
@Entity(tableName = "certifications")
data class CertificationEntity(
    @PrimaryKey val id: String,
    val certification: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)
