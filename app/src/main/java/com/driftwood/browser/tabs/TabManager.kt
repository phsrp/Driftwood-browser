package com.driftwood.browser.tabs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.engine.PromptUi
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse

class TabManager(private val app: DriftwoodApp) {
    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableStateOf<Long?>(null)
        private set
    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }

    /** A video or page is in full screen; the UI hides its bars. */
    var fullScreen by mutableStateOf(false)

    /** Called with (dy, scrollY) when the current tab scrolls. Set by the browser screen. */
    var onScroll: ((dy: Int, y: Int) -> Unit)? = null

    private var nextId = 1L
    private var restored = false

    // ---- Opening, switching and closing -------------------------------------------------

    fun newTab(url: String? = null, private: Boolean = false, select: Boolean = true, openerId: Long? = null): Tab {
        val tab = Tab(nextId++, private, url, openerId)
        val index = tabs.indexOfFirst { it.id == openerId }
        if (index >= 0) tabs.add(index + 1, tab) else tabs.add(tab)
        if (select) select(tab)
        return tab
    }

    fun select(tab: Tab) {
        currentId = tab.id
        ensureSession(tab)
    }

    fun close(tab: Tab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tab.session?.close()
        tab.session = null
        tabs.removeAt(index)
        if (currentId == tab.id) {
            val next = tabs.firstOrNull { it.id == tab.openerId }
                ?: tabs.filter { it.isPrivate == tab.isPrivate }.let { same ->
                    same.getOrNull(index.coerceAtMost(same.lastIndex)) ?: same.lastOrNull()
                }
                ?: tabs.lastOrNull()
            if (next != null) select(next) else newTab()
        }
    }

    fun closeAll(private: Boolean) {
        tabs.filter { it.isPrivate == private }.forEach { it.session?.close(); it.session = null }
        val wasCurrent = current?.isPrivate == private
        tabs.removeAll { it.isPrivate == private }
        if (wasCurrent || tabs.isEmpty()) {
            val next = tabs.lastOrNull()
            if (next != null) select(next) else newTab()
        }
    }

    /** Loads a URL in a tab and leaves the homepage. */
    fun load(tab: Tab, url: String) {
        if (url.isBlank()) return
        tab.showHome = false
        tab.url = url
        val session = tab.session
        if (session == null) {
            tab.pendingUrl = url
            ensureSession(tab)
        } else {
            session.loadUri(url)
        }
    }

    fun goHome(tab: Tab) {
        tab.showHome = true
        tab.startedFromHome = true
    }

    /** Opens a link from another app (when Driftwood is the default browser). */
    fun openExternalLink(url: String) {
        val tab = current
        if (tab != null && tab.showHome && !tab.isPrivate) load(tab, url) else newTab(url)
    }

    fun ensureSession(tab: Tab): GeckoSession {
        tab.session?.let { return it }
        val session = createSession(tab)
        session.open(app.runtime)
        tab.session = session
        tab.pendingUrl?.let {
            tab.pendingUrl = null
            tab.showHome = false
            session.loadUri(it)
        }
        return session
    }

    // ---- Saving open tabs between app launches ------------------------------------------

    fun restoreIfNeeded() {
        if (restored) return
        restored = true
        val saved = app.settings.internal.getString("open_tabs", null)
        if (saved != null) {
            runCatching {
                val json = JSONObject(saved)
                val urls = json.getJSONArray("urls")
                for (i in 0 until urls.length()) {
                    val url = urls.getString(i)
                    tabs += Tab(nextId++, false, url.ifEmpty { null })
                }
                tabs.getOrNull(json.optInt("selected", tabs.lastIndex))?.let { select(it) }
            }
        }
        if (tabs.isEmpty()) newTab() else if (current == null) select(tabs.last())
    }

    /** Saves normal (not incognito) tabs. Incognito tabs are never written anywhere. */
    fun persist() {
        val normal = tabs.filter { !it.isPrivate }
        val urls = JSONArray()
        normal.forEach { urls.put(if (it.showHome) "" else it.url) }
        val selected = normal.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        app.settings.internal.edit()
            .putString("open_tabs", JSONObject().put("urls", urls).put("selected", selected).toString())
            .apply()
    }

    // ---- Gecko callbacks ----------------------------------------------------------------

    private fun createSession(tab: Tab): GeckoSession {
        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(tab.isPrivate)
                .useTrackingProtection(true)
                .build(),
        )
        session.setNavigationDelegate(navigationDelegate(tab))
        session.setProgressDelegate(progressDelegate(tab))
        session.setContentDelegate(contentDelegate(tab))
        session.setScrollDelegate(scrollDelegate(tab))
        session.setPromptDelegate(app.prompts.delegateFor(tab))
        app.blockStats.attach(tab, session)
        return session
    }

    private fun navigationDelegate(tab: Tab) = object : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            if (url == null || url == "about:blank") return
            tab.url = url
            tab.lastScrollY = 0
            if (!tab.isPrivate && (url.startsWith("http://") || url.startsWith("https://"))) {
                app.history.record(url, tab.title)
            }
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            tab.canGoBack = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            tab.canGoForward = canGoForward
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
            val scheme = request.uri.substringBefore(':').lowercase()
            if (scheme in browserSchemes) return null
            // tel:, mailto:, intent:, market:, app links... hand them to Android.
            openInOtherApp(tab, request.uri)
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            // A link with target="_blank" or window.open(): new tab next to this one.
            // Gecko opens the returned session itself, so it must not be opened here.
            val newTab = newTab(null, tab.isPrivate, select = false, openerId = tab.id)
            newTab.showHome = false
            newTab.startedFromHome = false
            newTab.url = uri
            val newSession = createSession(newTab)
            newTab.session = newSession
            select(newTab)
            return GeckoResult.fromValue(newSession)
        }
    }

    private fun progressDelegate(tab: Tab) = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            tab.loading = true
            tab.progress = 5
            app.blockStats.onPageStart(tab)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            tab.loading = false
            tab.progress = 100
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            tab.progress = progress
        }
    }

    private fun contentDelegate(tab: Tab) = object : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            tab.title = title.orEmpty()
            if (!tab.isPrivate && !title.isNullOrBlank() && tab.url.startsWith("http")) {
                app.history.updateTitle(tab.url, title)
            }
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            app.downloader.handle(tab, response)
        }

        override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: ContextElement) {
            val link = element.linkUri
            val image = element.srcUri.takeIf { element.type == ContextElement.TYPE_IMAGE }
            if (link == null && image == null) return
            app.prompts.push(PromptUi.ContextMenu(tab, link, image, element.linkText ?: element.title))
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            this@TabManager.fullScreen = fullScreen
        }

        override fun onCloseRequest(session: GeckoSession) {
            close(tab)
        }

        override fun onCrash(session: GeckoSession) = recover(tab)

        override fun onKill(session: GeckoSession) = recover(tab)
    }

    private fun scrollDelegate(tab: Tab) = object : GeckoSession.ScrollDelegate {
        override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
            val dy = scrollY - tab.lastScrollY
            tab.lastScrollY = scrollY
            if (tab.id == currentId) onScroll?.invoke(dy, scrollY)
        }
    }

    /** The page's process crashed or was killed by Android: start a fresh session. */
    private fun recover(tab: Tab) {
        tab.session = null
        tab.loading = false
        if (!tab.showHome) tab.pendingUrl = tab.url
        if (tab.id == currentId) {
            fullScreen = false
            select(tab)
        }
    }

    private fun openInOtherApp(tab: Tab, uri: String) {
        try {
            val intent = if (uri.startsWith("intent:")) {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                app.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // intent: links often carry a web fallback for when the app isn't installed.
                intent.getStringExtra("browser_fallback_url")?.let { load(tab, it) }
                    ?: app.prompts.toast("No app can open this link")
            }
        } catch (e: Exception) {
            app.prompts.toast("Couldn't open this link")
        }
    }

    private companion object {
        val browserSchemes = setOf(
            "http", "https", "about", "data", "blob", "file", "moz-extension",
            "javascript", "view-source", "resource", "chrome",
        )
    }
}
