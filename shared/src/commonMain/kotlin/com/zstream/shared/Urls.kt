package com.zstream.shared

/**
 * App-wide backend/CDN endpoints. Moved to :shared so commonMain code (TMDB models, plugin
 * wire-format) doesn't need an Android-only lookup for URL building. app/'s own
 * com.zstream.android.Urls re-exports these constants so existing Android call sites are
 * unaffected.
 */
object Urls {
    const val BACKEND = "https://court.fontaine.lol/"
    const val TMDB_BASE = "https://api.themoviedb.org/3/"
    const val TMDB_IMAGE = "https://image.tmdb.org/t/p/"
    const val IMDB_GRAPHQL = "https://api.graphql.imdb.com/"
    const val PLUGIN_MANIFEST = "https://raw.githubusercontent.com/alturyxx-gif/zstream-plugin-releases/main/manifest.json"
    const val APP_GITHUB_REPO = "https://github.com/alturyxx-gif/ZStream-Android"
    const val DISCORD_LINK = "https://discord.com/invite/wmbWfk4SGy"
    const val NOTIFICATIONS_RSS = "https://zstream.mov/notifications.xml"
    const val PASSKEY_RP_ID = "zstream.mov"
    const val RYBBIT_BASE = "https://justice.fontaine.lol/api/"
}
