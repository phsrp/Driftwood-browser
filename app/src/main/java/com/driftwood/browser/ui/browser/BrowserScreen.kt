package com.driftwood.browser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.driftwood.browser.MainActivity
import com.driftwood.browser.app
import com.driftwood.browser.data.BarPosition
import com.driftwood.browser.tabs.Tab
import com.driftwood.browser.ui.Screen
import com.driftwood.browser.ui.home.HomeScreen
import com.driftwood.browser.ui.theme.LocalPalette
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs

@Composable
fun BrowserScreen(activity: MainActivity, navigate: (Screen) -> Unit) {
    val app = activity.app
    val tabs = app.tabs
    val tab = tabs.current ?: return
    val settings by app.settings.state.collectAsState()
    val palette = LocalPalette.current
    val focusManager = LocalFocusManager.current
    val barOnTop = settings.barPosition == BarPosition.TOP
    val fullScreen = tabs.fullScreen

    var barVisible by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }

    // Hide the bar while scrolling: the bottom bar hides when scrolling down,
    // the top bar hides when scrolling up. It always shows at the top of a page.
    DisposableEffect(barOnTop) {
        tabs.onScroll = { dy, y ->
            when {
                y <= 0 -> barVisible = true
                abs(dy) < 6 -> Unit
                barOnTop -> barVisible = dy > 0
                else -> barVisible = dy < 0
            }
        }
        onDispose { tabs.onScroll = null }
    }
    LaunchedEffect(tab.id, tab.url, tab.showHome) { barVisible = true }

    // One GeckoView for the whole app; switching tabs swaps the session shown in it.
    val geckoView = remember { GeckoView(activity).apply { setAutofillEnabled(true) } }
    LaunchedEffect(tab, tab.session) { geckoView.setSession(tabs.ensureSession(tab)) }

    LaunchedEffect(fullScreen) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullScreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        when {
            fullScreen -> tab.session?.exitFullScreen()
            editing -> focusManager.clearFocus()
            !tab.showHome && tab.canGoBack -> tab.session?.goBack()
            !tab.showHome && tab.startedFromHome -> tabs.goHome(tab)
            tab.openerId != null || tab.isPrivate -> tabs.close(tab)
            else -> activity.moveTaskToBack(true)
        }
    }

    val bar = @Composable {
        AnimatedVisibility(
            visible = !fullScreen && (barVisible || editing || tab.showHome),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            AddressBar(
                activity = activity,
                tab = tab,
                onEditingChange = { editing = it },
                navigate = navigate,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .then(if (fullScreen) Modifier else Modifier.systemBarsPadding())
            .imePadding(),
    ) {
        if (barOnTop) bar()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { geckoView }, modifier = Modifier.fillMaxSize())
            if (tab.showHome) {
                HomeScreen(app = app, isPrivate = tab.isPrivate, onOpen = { url ->
                    focusManager.clearFocus()
                    tabs.load(tab, url)
                })
            }
            // Drawn over the page edge next to the bar, so the page doesn't jump.
            ProgressLine(tab, Modifier.align(if (barOnTop) Alignment.TopCenter else Alignment.BottomCenter))
            // Download progress floats over the page, next to the address bar.
            DownloadBar(app, Modifier.align(if (barOnTop) Alignment.TopCenter else Alignment.BottomCenter))
        }
        if (!barOnTop) bar()
    }
}

@Composable
private fun ProgressLine(tab: Tab, modifier: Modifier) {
    if (!tab.loading || tab.showHome) return
    LinearProgressIndicator(
        progress = { tab.progress / 100f },
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = LocalPalette.current.accent,
        trackColor = Color.Transparent,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
