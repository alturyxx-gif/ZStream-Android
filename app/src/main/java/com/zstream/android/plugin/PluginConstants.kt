package com.zstream.android.plugin

import com.zstream.android.Urls

/**
 * Single source of truth for plugin-related constants.
 * The manifest URL is defined in Urls.kt — update it there if the CDN changes.
 */
internal object PluginConstants {
    /** URL of the plugin manifest JSON. */
    val MANIFEST_URL get() = Urls.PLUGIN_MANIFEST

    /** Fully-qualified class name the plugin APK must expose. */
    const val ENTRY_CLASS = "com.zstream.plugin.Entry"

    /** Directory name inside filesDir where plugin APKs are stored. */
    const val PLUGIN_DIR = "plugins"

    /** Directory name inside codeCacheDir for DexClassLoader optimised output. */
    const val DEX_CACHE_DIR = "plugin-dex"
}
