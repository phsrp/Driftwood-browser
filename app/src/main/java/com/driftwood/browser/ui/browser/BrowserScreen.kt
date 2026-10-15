package com.driftwood.browser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

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

    // The bar slides over the page instead of resizing it. Resizing made Firefox lay the
    // page out again on every animation frame, which is what caused the stutter.
    var barHeight by remember { mutableIntStateOf(0) }
    val showBar = !fullScreen && (barVisible || editing || tab.showHome)
    val shown = animateFloatAsState(if (showBar) 1f else 0f, tween(180), label = "bar")
    val density = LocalDensity.current
    val barHeightDp = with(density) { barHeight.toDp() }

    // Tell Firefox how tall the bar is and how much of the page bottom it covers right now,
    // so things fixed to the bottom of a page (cookie banners, chat buttons) stay visible.
    LaunchedEffect(barHeight, fullScreen) { geckoView.setDynamicToolbarMaxHeight(if (fullScreen) 0 else barHeight) }
    LaunchedEffect(barOnTop) {
        snapshotFlow { (barHeight * shown.value).roundToInt() }.collect { visible ->
            geckoView.setVerticalClipping(if (barOnTop) 0 else -visible)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .then(if (fullScreen) Modifier else Modifier.systemBarsPadding())
            .imePadding()
            .clipToBounds(),
    ) {
        AndroidView(
            factory = { geckoView },
            modifier = Modifier
                .fillMaxSize()
                // With the bar on top, the page moves down with it (like Firefox does).
                .graphicsLayer { translationY = if (barOnTop) barHeight * shown.value else 0f },
        )
        if (tab.showHome) {
            Box(Modifier.fillMaxSize().padding(top = if (barOnTop) barHeightDp else 0.dp, bottom = if (barOnTop) 0.dp else barHeightDp)) {
                HomeScreen(app = app, isPrivate = tab.isPrivate, onOpen = { url ->
                    focusManager.clearFocus()
                    tabs.load(tab, url)
                })
            }
        }

        // The loading line and download cards sit right next to the bar and move with it.
        val edge = if (barOnTop) Alignment.TopCenter else Alignment.BottomCenter
        val followBar = Modifier.graphicsLayer {
            val offset = barHeight * shown.value
            translationY = if (barOnTop) offset else -offset
        }
        Box(Modifier.align(edge).then(followBar)) { ProgressLine(tab, Modifier) }
        DownloadBar(app, Modifier.align(edge).then(followBar))

        Box(
            Modifier
                .align(edge)
                .onSizeChanged { barHeight = it.height }
                .graphicsLayer {
                    val hidden = size.height * (1f - shown.value)
                    translationY = if (barOnTop) -hidden else hidden
                },
        ) {
            AddressBar(
                activity = activity,
                tab = tab,
                onEditingChange = { editing = it },
                navigate = navigate,
            )
        }
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
