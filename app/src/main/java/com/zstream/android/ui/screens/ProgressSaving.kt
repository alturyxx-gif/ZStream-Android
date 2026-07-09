package com.zstream.android.ui.screens

const val PROGRESS_SAVE_INTERVAL_MS = 3000L

/** Mirrors p-stream ProgressSaver guards: skip under 20s watched, skip within last 2 minutes of a known duration. */
fun shouldPersistProgress(watchedSec: Long, durationSec: Long): Boolean =
    watchedSec >= 20 && (durationSec <= 0 || (durationSec - watchedSec) >= 120)
