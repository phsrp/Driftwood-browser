package com.driftwood.browser.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.R
import com.driftwood.browser.data.Shortcut
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.ui.common.LetterBadge
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.common.TitleUrlDialog
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette

private const val COLUMNS = 4

/** The clean start page: the name, and a grid of shortcuts you can edit. */
@Composable
fun HomeScreen(app: DriftwoodApp, isPrivate: Boolean, onOpen: (String) -> Unit) {
    val palette = LocalPalette.current
    val shortcuts by app.shortcuts.items.collectAsState()
    val engine = app.settings.state.collectAsState().value.searchEngine
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        if (isPrivate) {
            TIcon(Tabler.Spy, null, tint = palette.accent, size = 44.dp)
            Spacer(Modifier.height(12.dp))
            Text("Incognito", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = palette.text)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pages you open here aren't saved to your history, and their cookies are deleted when you close all incognito tabs. Downloads and bookmarks you make are still kept.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.subtle,
                textAlign = TextAlign.Center,
            )
        } else {
            // The app icon: the sand background and the log-and-waves drawing, layered.
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))) {
                Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Driftwood",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(40.dp))

        val tiles: List<Shortcut?> = shortcuts + listOf(null) // null = the "add" tile
        tiles.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { shortcut ->
                    if (shortcut == null) {
                        Tile("Add", onClick = { adding = true }) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(palette.surface),
                                contentAlignment = Alignment.Center,
                            ) { TIcon(Tabler.Plus, null, tint = palette.subtle) }
                        }
                    } else {
                        Tile(shortcut.title, onClick = { onOpen(shortcut.url) }, onLongClick = { editing = shortcut }) {
                            LetterBadge(shortcut.title)
                        }
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.width(72.dp)) }
            }
            Spacer(Modifier.height(18.dp))
        }
        Text(
            "Long-press a shortcut to edit or remove it",
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
        )
    }

    if (adding) {
        TitleUrlDialog("Add shortcut", "", "", onDismiss = { adding = false }, onSave = { title, url ->
            app.shortcuts.add(title, UrlInput.toUrl(url, engine))
            adding = false
        })
    }
    editing?.let { shortcut ->
        TitleUrlDialog(
            "Edit shortcut", shortcut.title, shortcut.url,
            onDismiss = { editing = null },
            onSave = { title, url ->
                app.shortcuts.update(shortcut.id, title, UrlInput.toUrl(url, engine))
                editing = null
            },
            onDelete = {
                app.shortcuts.delete(shortcut.id)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(label: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, icon: @Composable () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalPalette.current.subtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
