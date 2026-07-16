package com.zstream.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseDatesResponse(val results: List<ReleaseDatesCountry> = emptyList())

@Serializable
data class ReleaseDatesCountry(
    @SerialName("iso_3166_1") val country: String,
    @SerialName("release_dates") val releaseDates: List<ReleaseDateEntry> = emptyList(),
)

@Serializable
data class ReleaseDateEntry(val certification: String? = null)

@Serializable
data class ContentRatingsResponse(val results: List<ContentRatingCountry> = emptyList())

@Serializable
data class ContentRatingCountry(
    @SerialName("iso_3166_1") val country: String,
    val rating: String? = null,
)

@Serializable
data class CollectionDetails(
    val id: Int,
    val name: String,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val parts: List<CollectionPart> = emptyList(),
)

@Serializable
data class CollectionPart(
    val id: Int,
    val title: String,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)
