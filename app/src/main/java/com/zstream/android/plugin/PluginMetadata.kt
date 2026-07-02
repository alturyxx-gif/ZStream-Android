package com.zstream.android.plugin

/**
 * Persisted record describing a plugin file on disk.
 * Serialized to/from JSON via Gson and stored as a String in DataStore.
 *
 * Two instances may exist simultaneously:
 *  - active:  the plugin currently loaded in this session
 *  - staged:  a verified newer plugin downloaded in the background,
 *             promoted to active on the next app launch
 */
data class PluginMetadata(
    val version: Int,
    val hash: String,        // "sha256:<lowercase-hex>"
    val fileName: String,    // e.g. "plugin-v7.apk"
    val activatedAt: Long,   // epoch seconds
)
