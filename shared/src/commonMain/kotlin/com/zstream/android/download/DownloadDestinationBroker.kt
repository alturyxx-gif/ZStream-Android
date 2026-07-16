package com.zstream.android.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class DownloadDestinationChoice {
    object AppFolder : DownloadDestinationChoice()
    data class SdCard(val treeUri: String) : DownloadDestinationChoice()
}

/**
 * One shared "where should this download go" prompt for every download entry point (the
 * player's quality picker, and the one-tap download buttons on Detail/LocalPlayer screens) --
 * mounted once at the app root (see NavGraph's DownloadDestinationPrompt) so none of those call
 * sites need their own dialog/SAF-picker wiring. Mirrors OpenDownloadsNavigation's and
 * ReleaseUpdateManager's global-signal pattern.
 */
object DownloadDestinationBroker {
    private val _pending = MutableStateFlow(false)
    val pending = _pending.asStateFlow()

    // Serializes concurrent choose() calls (e.g. a "download season" loop) so a second caller
    // waits for the first prompt to resolve instead of clobbering its deferred.
    private val mutex = Mutex()
    private var deferred: CompletableDeferred<DownloadDestinationChoice?>? = null

    /** Suspends until the user answers the prompt. Null means the user dismissed/cancelled it. */
    suspend fun choose(): DownloadDestinationChoice? = mutex.withLock {
        val d = CompletableDeferred<DownloadDestinationChoice?>()
        deferred = d
        _pending.value = true
        try {
            d.await()
        } finally {
            _pending.value = false
        }
    }

    /** Called by the mounted prompt composable once the user picks an option (or dismisses it). */
    fun resolve(choice: DownloadDestinationChoice?) {
        deferred?.complete(choice)
        deferred = null
    }

    /**
     * Like [choose], but collapses the result to just the tree URI to pass to
     * DownloadResolver/DownloadRequest (null = app folder) wrapped so cancellation is
     * distinguishable from "app folder chosen" (both would otherwise be null). Returns null only
     * when the user cancelled the prompt -- callers should treat that as "don't download".
     */
    suspend fun chooseTreeUri(): DestinationResult? = when (val choice = choose()) {
        is DownloadDestinationChoice.AppFolder -> DestinationResult(null)
        is DownloadDestinationChoice.SdCard -> DestinationResult(choice.treeUri)
        null -> null
    }
}

/** [treeUri] null means the app's own ZStream folder; non-null means the given SAF tree. */
data class DestinationResult(val treeUri: String?)
