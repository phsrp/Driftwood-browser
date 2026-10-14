package com.driftwood.browser.ui.extensions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.engine.ExtensionManager
import com.driftwood.browser.engine.PromptUi
import com.driftwood.browser.ui.common.ListRow
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.SectionHeader
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette
import org.mozilla.geckoview.WebExtension

@Composable
fun ExtensionsScreen(app: DriftwoodApp, onBack: () -> Unit, open: (String) -> Unit) {
    val installed by app.extensions.installed.collectAsState()
    var selected by remember { mutableStateOf<WebExtension?>(null) }
    LaunchedEffect(Unit) { app.extensions.refresh() }

    ScreenScaffold("Extensions", onBack) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                ListRow("Get extensions", "Browse Firefox add-ons for Android", Tabler.ExternalLink, onClick = {
                    open(ExtensionManager.ADDONS_PAGE)
                })
                ListRow("Install from a link", "Paste an addons.mozilla.org or .xpi link", Tabler.Link, onClick = {
                    app.prompts.push(PromptUi.TextInput("Install extension", "Link to the add-on page or .xpi file", "") { text ->
                        if (!text.isNullOrBlank()) app.extensions.install(text)
                    })
                })
                SectionHeader("Installed")
                if (installed.isEmpty()) {
                    Text(
                        "No extensions yet. uBlock Origin installs automatically the first time Driftwood is online.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalPalette.current.subtle,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                }
            }
            items(installed, key = { it.id }) { ext ->
                val meta = ext.metaData
                ListRow(
                    meta.name ?: ext.id,
                    listOfNotNull(
                        "v${meta.version}",
                        if (meta.allowedInPrivateBrowsing) "Runs in incognito" else null,
                    ).joinToString(" · "),
                    Tabler.Puzzle,
                    onClick = { selected = ext },
                ) {
                    Switch(
                        checked = meta.enabled,
                        onCheckedChange = { app.extensions.setEnabled(ext, it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = LocalPalette.current.accent,
                            checkedThumbColor = LocalPalette.current.onAccent,
                        ),
                    )
                }
            }
        }
    }

    selected?.let { ext ->
        val meta = ext.metaData
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(meta.name ?: ext.id) },
            text = {
                Column {
                    meta.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    meta.optionsPageUrl?.let { url ->
                        ListRow("Settings", "Open the extension's own settings", Tabler.Settings, onClick = {
                            selected = null
                            open(url)
                        })
                    }
                    ListRow("Run in incognito tabs", null, Tabler.Spy, onClick = {
                        app.extensions.setAllowedInPrivate(ext, !meta.allowedInPrivateBrowsing)
                        selected = null
                    }) {
                        Switch(
                            checked = meta.allowedInPrivateBrowsing,
                            onCheckedChange = {
                                app.extensions.setAllowedInPrivate(ext, it)
                                selected = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = {
                    selected = null
                    app.prompts.confirm("Remove ${meta.name ?: "extension"}?", "You can install it again later.", "Remove") {
                        app.extensions.uninstall(ext)
                    }
                }) { Text("Remove", color = LocalPalette.current.danger) }
            },
        )
    }
}
