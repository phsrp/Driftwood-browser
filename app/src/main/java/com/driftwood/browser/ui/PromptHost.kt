package com.driftwood.browser.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driftwood.browser.MainActivity
import com.driftwood.browser.app
import com.driftwood.browser.engine.PromptController
import com.driftwood.browser.engine.PromptUi
import com.driftwood.browser.ui.common.Field
import com.driftwood.browser.ui.common.ListRow
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette

/** Shows the first waiting dialog from websites, extensions and the app itself. */
@Composable
fun PromptHost(activity: MainActivity) {
    val prompts = activity.app.prompts
    val prompt = prompts.queue.firstOrNull() ?: return
    when (prompt) {
        is PromptUi.Alert -> SimpleDialog(prompt.title, prompt.message, "OK", null,
            onConfirm = { prompts.finish(prompt) { prompt.onDone() } },
            onCancel = { prompts.finish(prompt) { prompt.onDone() } })

        is PromptUi.Confirm -> SimpleDialog(prompt.title, prompt.message, prompt.positive, prompt.negative,
            onConfirm = { prompts.finish(prompt) { prompt.onResult(true) } },
            onCancel = { prompts.finish(prompt) { prompt.onResult(false) } })

        is PromptUi.TextInput -> TextInputDialog(prompt) { text -> prompts.finish(prompt) { prompt.onResult(text) } }

        is PromptUi.Choice -> ChoiceDialog(prompt) { ids -> prompts.finish(prompt) { prompt.onResult(ids) } }

        is PromptUi.SaveLogin -> SimpleDialog(
            "Save password?",
            "Save the password for ${prompt.username.ifEmpty { "this login" }} on ${prompt.host}? It's stored encrypted and fills only on ${prompt.host}.",
            "Save", "Not now",
            onConfirm = { prompts.finish(prompt) { prompt.onResult(true) } },
            onCancel = { prompts.finish(prompt) { prompt.onResult(false) } },
        )

        is PromptUi.SelectLogin -> AlertDialog(
            onDismissRequest = { prompts.finish(prompt) { prompt.onResult(null) } },
            title = { Text("Sign in to ${prompt.host}") },
            text = {
                Column {
                    prompt.usernames.forEachIndexed { i, name ->
                        ListRow(name, null, Tabler.Key, onClick = { prompts.finish(prompt) { prompt.onResult(i) } })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { prompts.finish(prompt) { prompt.onResult(null) } }) { Text("Cancel") } },
        )

        is PromptUi.ExtensionInstall -> ExtensionInstallDialog(prompt) { allow, private ->
            prompts.finish(prompt) { prompt.onResult(allow, private) }
        }

        is PromptUi.ContextMenu -> ContextMenuDialog(activity, prompt) { prompts.finish(prompt) }
    }
}

@Composable
private fun SimpleDialog(
    title: String?,
    message: String?,
    confirm: String,
    cancel: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        text = message?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = cancel?.let { { TextButton(onClick = onCancel) { Text(it) } } },
    )
}

@Composable
private fun TextInputDialog(prompt: PromptUi.TextInput, onResult: (String?) -> Unit) {
    var text by remember { mutableStateOf(prompt.default) }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = prompt.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        text = {
            Column {
                prompt.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.width(8.dp))
                Field(text, { text = it }, "")
            }
        },
        confirmButton = { TextButton(onClick = { onResult(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceDialog(prompt: PromptUi.Choice, onResult: (List<String>?) -> Unit) {
    val palette = LocalPalette.current
    var checked by remember { mutableStateOf(prompt.items.filter { it.selected }.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = prompt.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(prompt.items) { _, item ->
                    if (item.isHeader) {
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = palette.accent,
                            modifier = Modifier.padding(vertical = 8.dp))
                        return@itemsIndexed
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.enabled) {
                                if (prompt.multiple) {
                                    checked = if (item.id in checked) checked - item.id else checked + item.id
                                } else {
                                    onResult(listOf(item.id))
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (prompt.multiple) {
                            Checkbox(checked = item.id in checked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                !item.enabled -> palette.muted
                                !prompt.multiple && item.selected -> palette.accent
                                else -> palette.text
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.multiple) TextButton(onClick = { onResult(checked.toList()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Cancel") } },
    )
}

@Composable
private fun ExtensionInstallDialog(prompt: PromptUi.ExtensionInstall, onResult: (Boolean, Boolean) -> Unit) {
    var allowPrivate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onResult(false, false) },
        icon = { TIcon(Tabler.Puzzle, null, tint = LocalPalette.current.accent) },
        title = { Text("Add ${prompt.name}?") },
        text = {
            Column {
                if (prompt.permissions.isNotEmpty()) {
                    Text("It will be able to:", style = MaterialTheme.typography.bodyMedium)
                    prompt.permissions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { allowPrivate = !allowPrivate }.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                    Text("Also run in incognito tabs", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onResult(true, allowPrivate) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { onResult(false, false) }) { Text("Cancel") } },
    )
}

@Composable
private fun ContextMenuDialog(activity: MainActivity, prompt: PromptUi.ContextMenu, onDone: () -> Unit) {
    val app = activity.app
    val tabs = app.tabs
    val target = prompt.link ?: prompt.image!!

    @Composable
    fun action(icon: ImageVector, label: String, run: () -> Unit) {
        ListRow(label, null, icon, onClick = { onDone(); run() })
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Text(
                prompt.label?.takeIf { it.isNotBlank() } ?: target,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                prompt.link?.let { link ->
                    action(Tabler.Plus, "Open in new tab") { tabs.newTab(link, private = prompt.tab.isPrivate, select = false, openerId = prompt.tab.id).also { app.prompts.toast("Opened in a new tab") } }
                    action(Tabler.Spy, "Open in incognito tab") { tabs.newTab(link, private = true) }
                    action(Tabler.Copy, "Copy link") {
                        PromptController.copyToClipboard(app, "Link", link)
                        app.prompts.toast("Link copied")
                    }
                    action(Tabler.Share, "Share link") {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        activity.startActivity(Intent.createChooser(send, null))
                    }
                    action(Tabler.Download, "Download link") { app.downloader.download(link, prompt.tab.isPrivate) }
                }
                prompt.image?.let { image ->
                    action(Tabler.ExternalLink, "Open image in new tab") { tabs.newTab(image, private = prompt.tab.isPrivate) }
                    action(Tabler.Download, "Save image") { app.downloader.download(image, prompt.tab.isPrivate) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}
