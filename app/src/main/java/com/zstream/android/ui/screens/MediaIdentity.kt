package com.zstream.android.ui.screens

import com.zstream.android.data.model.Media

internal fun Media.stableUiKey(): String = "$type:$id"

internal fun List<Media>.distinctByMediaIdentity(): List<Media> =
    distinctBy { it.stableUiKey() }
