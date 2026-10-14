package com.driftwood.browser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession

/**
 * One browser tab. The Gecko session is created only when the tab is first
 * shown, so restored tabs cost nothing until you open them.
 */
class Tab(
    val id: Long,
    val isPrivate: Boolean,
    initialUrl: String?,
    /** The tab that opened this one (for links that open a new window). */
    val openerId: Long? = null,
) {
    var session by mutableStateOf<GeckoSession?>(null)

    /** URL to load as soon as the session exists. */
    var pendingUrl: String? = initialUrl

    var url by mutableStateOf(initialUrl ?: "")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** True while the tab shows the Driftwood homepage instead of a website. */
    var showHome by mutableStateOf(initialUrl == null)

    /** Whether "back" from the first page returns to the homepage (true) or closes the tab. */
    var startedFromHome = initialUrl == null

    var lastScrollY = 0

    /** Ads/trackers uBlock Origin blocked on the current page. */
    var ublockBlocked by mutableIntStateOf(0)

    /** Trackers Firefox's tracking protection blocked on the current page. */
    var trackersBlocked by mutableIntStateOf(0)
}
