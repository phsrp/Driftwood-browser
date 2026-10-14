package com.driftwood.browser.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.tabs.Tab
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

/**
 * Counts what got blocked, from two sources:
 * - uBlock Origin: it shows a "blocked on this page" number on its toolbar badge.
 *   Driftwood reads that badge for every page and adds it to a running total.
 * - Firefox's built-in tracking protection, which reports each tracker it blocks.
 */
class BlockStats(private val app: DriftwoodApp) {
    private val prefs = app.getSharedPreferences("block_stats", 0)

    var ublockTotal by mutableLongStateOf(prefs.getLong("ublock", 0))
        private set
    var trackers by mutableLongStateOf(prefs.getLong("trackers", 0))
        private set
    var social by mutableLongStateOf(prefs.getLong("social", 0))
        private set
    var fingerprinters by mutableLongStateOf(prefs.getLong("fingerprinters", 0))
        private set
    var cryptominers by mutableLongStateOf(prefs.getLong("cryptominers", 0))
        private set
    var since by mutableLongStateOf(prefs.getLong("since", 0L).takeIf { it > 0 } ?: now().also { prefs.edit().putLong("since", it).apply() })
        private set

    val trackerTotal: Long get() = trackers + social + fingerprinters + cryptominers

    private var ublock by mutableStateOf<WebExtension?>(null)

    /** Hooks a tab's new session up to both counters. */
    fun attach(tab: Tab, session: GeckoSession) {
        session.setContentBlockingDelegate(object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (!event.isBlocking) return
                val category = event.antiTrackingCategory
                when {
                    category and ContentBlocking.AntiTracking.FINGERPRINTING != 0 -> fingerprinters++
                    category and ContentBlocking.AntiTracking.CRYPTOMINING != 0 -> cryptominers++
                    category and ContentBlocking.AntiTracking.SOCIAL != 0 -> social++
                    category != 0 -> trackers++
                    else -> return
                }
                tab.trackersBlocked++
                save()
            }
        })
        ublock?.let { session.webExtensionController.setActionDelegate(it, badgeReader(tab)) }
    }

    /** Called when uBlock Origin is found or installed, so open tabs start counting too. */
    fun onUblockAvailable(extension: WebExtension) {
        if (ublock?.id == extension.id) return
        ublock = extension
        app.tabs.tabs.forEach { tab ->
            tab.session?.webExtensionController?.setActionDelegate(extension, badgeReader(tab))
        }
    }

    /** A new page started loading in [tab]: its per-page counts start from zero. */
    fun onPageStart(tab: Tab) {
        tab.ublockBlocked = 0
        tab.trackersBlocked = 0
    }

    fun reset() {
        ublockTotal = 0
        trackers = 0
        social = 0
        fingerprinters = 0
        cryptominers = 0
        since = now()
        save()
    }

    private fun badgeReader(tab: Tab) = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            // The badge shows the number blocked on the current page so far, e.g. "12".
            val count = action.badgeText?.trim()?.toIntOrNull() ?: return
            if (count > tab.ublockBlocked) {
                ublockTotal += count - tab.ublockBlocked
                save()
            }
            tab.ublockBlocked = count
        }
    }

    private fun save() {
        prefs.edit()
            .putLong("ublock", ublockTotal)
            .putLong("trackers", trackers)
            .putLong("social", social)
            .putLong("fingerprinters", fingerprinters)
            .putLong("cryptominers", cryptominers)
            .putLong("since", since)
            .apply()
    }

    private fun now() = System.currentTimeMillis()
}
