package com.driftwood.browser.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.tabs.Tab
import com.driftwood.browser.ui.common.IconBtn
import com.driftwood.browser.ui.common.LetterBadge
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette

@Composable
fun TabsScreen(app: DriftwoodApp, onDone: () -> Unit) {
    val tabs = app.tabs
    val palette = LocalPalette.current
    var showPrivate by remember { mutableStateOf(tabs.current?.isPrivate == true) }
    val list = tabs.tabs.filter { it.isPrivate == showPrivate }

    ScreenScaffold("Tabs", onBack = onDone, actions = {
        if (list.isNotEmpty()) {
            TextButton(onClick = { tabs.closeAll(showPrivate) }) { Text("Close all") }
        }
    }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Tabs (${tabs.tabs.count { !it.isPrivate }})", Tabler.Square, !showPrivate) { showPrivate = false }
            Chip("Incognito (${tabs.tabs.count { it.isPrivate }})", Tabler.Spy, showPrivate) { showPrivate = true }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { tab ->
                TabCard(tab, selected = tab.id == tabs.currentId, onOpen = {
                    tabs.select(tab)
                    onDone()
                }, onClose = { tabs.close(tab) })
            }
        }
        Button(
            onClick = {
                tabs.newTab(private = showPrivate)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            TIcon(if (showPrivate) Tabler.Spy else Tabler.Plus, null, tint = palette.onAccent, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(if (showPrivate) "New incognito tab" else "New tab")
        }
    }
}

@Composable
private fun Chip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) palette.accent else palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TIcon(icon, null, tint = if (selected) palette.onAccent else palette.subtle, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) palette.onAccent else palette.text)
    }
}

@Composable
private fun TabCard(tab: Tab, selected: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val palette = LocalPalette.current
    val title = when {
        tab.showHome -> "Homepage"
        tab.title.isNotBlank() -> tab.title
        else -> UrlInput.display(tab.url)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(2.dp, if (selected) palette.accent else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 6.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LetterBadge(title, size = 30.dp)
            Spacer(Modifier.weight(1f))
            IconBtn(Tabler.X, "Close tab", onClose, tint = palette.subtle)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp).height(40.dp),
        )
        Text(
            if (tab.showHome) "" else UrlInput.display(tab.url),
            style = MaterialTheme.typography.bodySmall,
            color = palette.subtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}
