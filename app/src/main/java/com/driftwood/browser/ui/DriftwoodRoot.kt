package com.driftwood.browser.ui

import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.driftwood.browser.MainActivity
import com.driftwood.browser.app
import com.driftwood.browser.ui.browser.BrowserScreen
import com.driftwood.browser.ui.extensions.ExtensionsScreen
import com.driftwood.browser.ui.library.BookmarksScreen
import com.driftwood.browser.ui.library.DownloadsScreen
import com.driftwood.browser.ui.library.HistoryScreen
import com.driftwood.browser.ui.passwords.PasswordsScreen
import com.driftwood.browser.ui.settings.SettingsScreen
import com.driftwood.browser.ui.stats.AdBlockingScreen
import com.driftwood.browser.ui.tabs.TabsScreen
import com.driftwood.browser.ui.theme.DriftwoodTheme
import com.driftwood.browser.ui.theme.activePalette

enum class Screen { BROWSER, TABS, BOOKMARKS, HISTORY, DOWNLOADS, SETTINGS, EXTENSIONS, PASSWORDS, ADBLOCK }

@Composable
fun DriftwoodRoot(activity: MainActivity) {
    val app = activity.app
    val settings by app.settings.state.collectAsState()
    val palette = activePalette(settings)

    // Status bar and navigation bar icons: dark on light themes, light on dark themes.
    LaunchedEffect(palette.isDark) {
        val style = if (palette.isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // A website asked to upload files: open Android's file picker.
    val fileRequest = app.prompts.fileRequest
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        app.prompts.fileRequest?.let { request ->
            request.complete(if (request.multiple) uris else uris.take(1))
        }
    }
    LaunchedEffect(fileRequest) {
        val request = fileRequest ?: return@LaunchedEffect
        val types = request.mimeTypes.ifEmpty { listOf("*/*") }
        runCatching { picker.launch(types.toTypedArray()) }.onFailure { request.complete(emptyList()) }
    }

    DriftwoodTheme(palette) {
        var screen by rememberSaveable { mutableStateOf(Screen.BROWSER) }
        val back = { screen = Screen.BROWSER }
        // Opens a URL in the current tab and returns to the browser.
        val open = { url: String ->
            app.tabs.current?.let { app.tabs.load(it, url) }
            screen = Screen.BROWSER
        }
        // Opens a URL in a new tab (extension pages, add-on store).
        val openNewTab = { url: String ->
            app.tabs.newTab(url)
            screen = Screen.BROWSER
        }

        Box(Modifier.fillMaxSize()) {
            // The browser always stays underneath, so pages keep running while
            // another screen is open on top.
            BrowserScreen(activity) { screen = it }
            if (screen != Screen.BROWSER) {
                BackHandler { back() }
                when (screen) {
                    Screen.TABS -> TabsScreen(app, onDone = back)
                    Screen.BOOKMARKS -> BookmarksScreen(app, back, open)
                    Screen.HISTORY -> HistoryScreen(app, back, open)
                    Screen.DOWNLOADS -> DownloadsScreen(app, back)
                    Screen.SETTINGS -> SettingsScreen(app, back) { screen = it }
                    Screen.EXTENSIONS -> ExtensionsScreen(app, back, openNewTab)
                    Screen.PASSWORDS -> PasswordsScreen(activity, app, back)
                    Screen.ADBLOCK -> AdBlockingScreen(app, back)
                    Screen.BROWSER -> Unit
                }
            }
            PromptHost(activity)
        }
    }
}
