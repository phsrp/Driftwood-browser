package com.driftwood.browser

import android.app.Application
import android.content.Context
import com.driftwood.browser.data.BookmarkRepo
import com.driftwood.browser.data.BrowserDb
import com.driftwood.browser.data.DownloadRepo
import com.driftwood.browser.data.HistoryRepo
import com.driftwood.browser.data.PasswordRepo
import com.driftwood.browser.data.SettingsStore
import com.driftwood.browser.data.ShortcutRepo
import com.driftwood.browser.engine.BlockStats
import com.driftwood.browser.engine.Downloader
import com.driftwood.browser.engine.Engine
import com.driftwood.browser.engine.ExtensionManager
import com.driftwood.browser.engine.PromptController
import com.driftwood.browser.tabs.TabManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.mozilla.geckoview.GeckoRuntime

/**
 * App-wide objects. Everything is created lazily, because GeckoView also starts
 * this class inside its own helper processes, where none of it may run.
 */
class DriftwoodApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings by lazy { SettingsStore(this) }
    private val db by lazy { BrowserDb(this) }
    val bookmarks by lazy { BookmarkRepo(db, scope) }
    val history by lazy { HistoryRepo(db, scope) }
    val downloads by lazy { DownloadRepo(db, scope) }
    val shortcuts by lazy { ShortcutRepo(db, scope) }
    val passwords by lazy { PasswordRepo(this) }

    val runtime: GeckoRuntime by lazy { Engine.createRuntime(this) }
    val prompts by lazy { PromptController(this) }
    val downloader by lazy { Downloader(this) }
    val extensions by lazy { ExtensionManager(this) }
    val blockStats by lazy { BlockStats(this) }
    val tabs by lazy { TabManager(this) }
}

val Context.app: DriftwoodApp get() = applicationContext as DriftwoodApp
