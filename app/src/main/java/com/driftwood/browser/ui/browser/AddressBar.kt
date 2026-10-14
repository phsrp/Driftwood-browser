package com.driftwood.browser.ui.browser

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.driftwood.browser.MainActivity
import com.driftwood.browser.app
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.tabs.Tab
import com.driftwood.browser.ui.Screen
import com.driftwood.browser.ui.common.IconBtn
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette

@Composable
fun AddressBar(
    activity: MainActivity,
    tab: Tab,
    onEditingChange: (Boolean) -> Unit,
    navigate: (Screen) -> Unit,
) {
    val app = activity.app
    val palette = LocalPalette.current
    val focusManager = LocalFocusManager.current
    val settings by app.settings.state.collectAsState()

    var focused by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue("")) }

    // While not editing, show the short form of the current address.
    LaunchedEffect(tab.id, tab.url, tab.showHome, focused) {
        if (!focused) value = TextFieldValue(if (tab.showHome) "" else UrlInput.display(tab.url))
    }

    fun submit() {
        val url = UrlInput.toUrl(value.text, settings.searchEngine)
        focusManager.clearFocus()
        if (url.isNotEmpty()) app.tabs.load(tab, url)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surfaceHigh)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leading: ImageVector = when {
                tab.isPrivate -> Tabler.Spy
                focused || tab.showHome -> Tabler.Search
                tab.url.startsWith("https://") -> Tabler.Lock
                else -> Tabler.World
            }
            TIcon(leading, null, tint = if (tab.isPrivate) palette.accent else palette.subtle, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.text),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (state.isFocused && !focused) {
                            val full = if (tab.showHome) "" else tab.url
                            value = TextFieldValue(full, TextRange(0, full.length))
                        }
                        focused = state.isFocused
                        onEditingChange(state.isFocused)
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                if (tab.isPrivate) "Search privately" else "Search or enter address",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.muted,
                            )
                        }
                        inner()
                    }
                },
            )
            when {
                focused && value.text.isNotEmpty() ->
                    IconBtn(Tabler.X, "Clear", { value = TextFieldValue("") }, tint = palette.subtle)
                !focused && !tab.showHome && tab.ublockBlocked + tab.trackersBlocked > 0 ->
                    BlockedBadge(tab.ublockBlocked + tab.trackersBlocked) { navigate(Screen.STATS) }
            }
        }

        if (!focused) {
            TabCountButton(app.tabs.tabs.count { it.isPrivate == tab.isPrivate }) { navigate(Screen.TABS) }
            BrowserMenu(activity, tab, navigate)
        }
    }
}

@Composable
private fun BlockedBadge(count: Int, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TIcon(Tabler.ShieldCheck, "Blocked on this page", tint = palette.accent, size = 16.dp)
        Spacer(Modifier.width(3.dp))
        Text(if (count > 999) "999+" else count.toString(), style = MaterialTheme.typography.labelSmall, color = palette.accent)
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TIcon(Tabler.Square, "Tabs", size = 26.dp)
        Text(
            if (count > 99) ":)" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BrowserMenu(activity: MainActivity, tab: Tab, navigate: (Screen) -> Unit) {
    val app = activity.app
    val palette = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    val bookmarks by app.bookmarks.items.collectAsState()
    val isBookmarked = !tab.showHome && bookmarks.any { it.url == tab.url }

    Box {
        IconBtn(Tabler.DotsVertical, "Menu", { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = palette.surface,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(Modifier.padding(horizontal = 4.dp)) {
                IconBtn(Tabler.ArrowLeft, "Back", { tab.session?.goBack(); open = false }, enabled = tab.canGoBack && !tab.showHome)
                IconBtn(Tabler.ArrowRight, "Forward", { tab.session?.goForward(); open = false }, enabled = tab.canGoForward)
                if (tab.loading) {
                    IconBtn(Tabler.X, "Stop", { tab.session?.stop(); open = false })
                } else {
                    IconBtn(Tabler.Refresh, "Reload", { tab.session?.reload(); open = false }, enabled = !tab.showHome)
                }
                IconBtn(
                    Tabler.Star,
                    if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    {
                        if (isBookmarked) {
                            app.bookmarks.removeUrl(tab.url)
                            app.prompts.toast("Bookmark removed")
                        } else {
                            app.bookmarks.add(tab.title.ifBlank { UrlInput.display(tab.url) }, tab.url)
                            app.prompts.toast("Bookmarked")
                        }
                        open = false
                    },
                    enabled = !tab.showHome,
                    tint = if (isBookmarked) palette.accent else MaterialTheme.colorScheme.onSurface,
                )
                IconBtn(Tabler.Share, "Share", {
                    open = false
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url)
                    activity.startActivity(Intent.createChooser(send, null))
                }, enabled = !tab.showHome)
            }
            HorizontalDivider(color = palette.surfaceHigh)
            MenuItem(Tabler.Home, "Homepage") { app.tabs.goHome(tab); open = false }
            MenuItem(Tabler.Plus, "New tab") { app.tabs.newTab(); open = false }
            MenuItem(Tabler.Spy, "New incognito tab") { app.tabs.newTab(private = true); open = false }
            HorizontalDivider(color = palette.surfaceHigh)
            MenuItem(Tabler.Bookmarks, "Bookmarks") { navigate(Screen.BOOKMARKS); open = false }
            MenuItem(Tabler.History, "History") { navigate(Screen.HISTORY); open = false }
            MenuItem(Tabler.Download, "Downloads") { navigate(Screen.DOWNLOADS); open = false }
            MenuItem(Tabler.ShieldCheck, "Blocked: ${tab.ublockBlocked + tab.trackersBlocked} on this page") {
                navigate(Screen.STATS); open = false
            }
            MenuItem(Tabler.Puzzle, "Extensions") { navigate(Screen.EXTENSIONS); open = false }
            MenuItem(Tabler.Settings, "Settings") { navigate(Screen.SETTINGS); open = false }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { TIcon(icon, null, size = 20.dp) },
        onClick = onClick,
    )
}
