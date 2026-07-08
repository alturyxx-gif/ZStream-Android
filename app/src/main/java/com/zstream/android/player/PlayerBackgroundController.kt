package com.zstream.android.player

/**
 * Bridges MainActivity.onUserLeaveHint() (called only when the user explicitly leaves via
 * home/recents, not on rotation or dialogs) to the currently composed PlayerScreen, since the
 * Activity can't hold a direct reference to the Compose-owned MpvPlayer/enterPip logic.
 */
object PlayerBackgroundController {
    @Volatile
    var onUserLeaveHint: (() -> Unit)? = null
}
