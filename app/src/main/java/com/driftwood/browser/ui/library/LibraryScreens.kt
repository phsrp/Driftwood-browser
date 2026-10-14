package com.driftwood.browser.ui.library

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.data.Bookmark
import com.driftwood.browser.importer.ChromeBookmarkParser
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.ui.common.EmptyState
import com.driftwood.browser.ui.common.Field
import com.driftwood.browser.ui.common.IconBtn
import com.driftwood.browser.ui.common.ListRow
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.SectionHeader
import com.driftwood.browser.ui.common.TitleUrlDialog
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Opens a picker for Chrome's bookmarks HTML file and imports it. */
@Composable
fun rememberBookmarkImporter(app: DriftwoodApp): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val html = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    ChromeBookmarkParser.parse(html)
                }.getOrNull()
            }
            when {
                parsed == null -> app.prompts.toast("Couldn't read that file")
                parsed.isEmpty() -> app.prompts.toast("No bookmarks found. Is this Chrome's bookmarks export?")
                else -> {
                    val added = app.bookmarks.importAll(parsed)
                    app.prompts.toast("Imported $added bookmarks" + if (added < parsed.size) " (${parsed.size - added} already saved)" else "")
                }
            }
        }
    }
    return { launcher.launch(arrayOf("text/html", "text/plain", "application/octet-stream")) }
}

@Composable
fun BookmarksScreen(app: DriftwoodApp, onBack: () -> Unit, open: (String) -> Unit) {
    val bookmarks by app.bookmarks.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    val importBookmarks = rememberBookmarkImporter(app)
    val engine = app.settings.state.collectAsState().value.searchEngine

    ScreenScaffold("Bookmarks", onBack, actions = {
        IconBtn(Tabler.FileImport, "Import from Chrome", importBookmarks)
    }) {
        if (bookmarks.isEmpty()) {
            EmptyState(
                Tabler.Bookmarks,
                "No bookmarks yet. Tap ⋮ then ☆ on a page to add one, or import from Chrome with the button above.",
            )
            return@ScreenScaffold
        }
        Field(query, { query = it }, "Search bookmarks", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val filtered = bookmarks.filter {
            query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true)
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            filtered.groupBy { it.folder }.forEach { (folder, items) ->
                if (folder.isNotEmpty()) item(key = "folder:$folder") { SectionHeader(folder) }
                items(items, key = { it.id }) { bookmark ->
                    ListRow(
                        title = bookmark.title,
                        subtitle = UrlInput.display(bookmark.url),
                        icon = Tabler.Bookmark,
                        onClick = { open(bookmark.url) },
                        onLongClick = { editing = bookmark },
                    ) {
                        IconBtn(Tabler.Pencil, "Edit", { editing = bookmark }, tint = LocalPalette.current.subtle)
                    }
                }
            }
        }
    }

    editing?.let { bookmark ->
        TitleUrlDialog(
            "Edit bookmark", bookmark.title, bookmark.url,
            onDismiss = { editing = null },
            onSave = { title, url ->
                app.bookmarks.update(bookmark.id, title, UrlInput.toUrl(url, engine))
                editing = null
            },
            onDelete = {
                app.bookmarks.delete(bookmark.id)
                editing = null
            },
        )
    }
}

@Composable
fun HistoryScreen(app: DriftwoodApp, onBack: () -> Unit, open: (String) -> Unit) {
    val history by app.history.items.collectAsState()
    var query by remember { mutableStateOf("") }

    ScreenScaffold("History", onBack, actions = {
        if (history.isNotEmpty()) {
            IconBtn(Tabler.Trash, "Clear history", {
                app.prompts.confirm("Clear history?", "This removes every page from your history.", "Clear") {
                    app.history.clear()
                }
            })
        }
    }) {
        if (history.isEmpty()) {
            EmptyState(Tabler.History, "Pages you visit will show up here. Incognito tabs are never saved.")
            return@ScreenScaffold
        }
        Field(query, { query = it }, "Search history", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val filtered = history.filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
        LazyColumn(Modifier.fillMaxWidth()) {
            filtered.groupBy { dayLabel(it.visited) }.forEach { (day, entries) ->
                item(key = "day:$day") { SectionHeader(day) }
                items(entries, key = { it.id }) { entry ->
                    ListRow(
                        title = entry.title.ifBlank { UrlInput.display(entry.url) },
                        subtitle = UrlInput.display(entry.url) + " · " +
                            DateUtils.formatDateTime(app, entry.visited, DateUtils.FORMAT_SHOW_TIME),
                        icon = Tabler.World,
                        onClick = { open(entry.url) },
                    ) {
                        IconBtn(Tabler.X, "Remove", { app.history.delete(entry.id) }, tint = LocalPalette.current.subtle)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(app: DriftwoodApp, onBack: () -> Unit) {
    val downloads by app.downloads.items.collectAsState()
    val context = LocalContext.current
    ScreenScaffold("Downloads", onBack) {
        if (downloads.isEmpty()) {
            EmptyState(Tabler.Download, "Files you download are saved to your phone's Downloads folder and listed here.")
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(downloads, key = { it.id }) { download ->
                ListRow(
                    title = download.name,
                    subtitle = Formatter.formatShortFileSize(context, download.size) + " · " +
                        DateUtils.getRelativeTimeSpanString(download.created),
                    icon = Tabler.File,
                    onClick = { app.downloader.open(download.uri, download.mime) },
                ) {
                    IconBtn(Tabler.X, "Remove from list", { app.downloads.delete(download.id) }, tint = LocalPalette.current.subtle)
                }
            }
        }
    }
}

private fun dayLabel(time: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(day, today)) return "Today"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(day, today)) return "Yesterday"
    return android.text.format.DateFormat.format("EEEE d MMMM yyyy", day).toString()
}
