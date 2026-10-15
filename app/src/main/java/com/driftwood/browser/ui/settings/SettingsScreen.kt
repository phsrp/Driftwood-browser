package com.driftwood.browser.ui.settings

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.data.BarPosition
import com.driftwood.browser.search.SearchEngine
import com.driftwood.browser.ui.Screen
import com.driftwood.browser.ui.common.ListRow
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.SectionHeader
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.library.rememberBookmarkImporter
import com.driftwood.browser.ui.passwords.rememberPasswordImporter
import com.driftwood.browser.ui.theme.LocalPalette
import com.driftwood.browser.ui.theme.ThemeId
import org.mozilla.geckoview.StorageController

@Composable
fun SettingsScreen(app: DriftwoodApp, onBack: () -> Unit, navigate: (Screen) -> Unit) {
    val settings by app.settings.state.collectAsState()
    val palette = LocalPalette.current
    val context = LocalContext.current
    val importBookmarks = rememberBookmarkImporter(app)
    val importPasswords = rememberPasswordImporter(app)
    var showLicences by remember { mutableStateOf(false) }

    val roleManager = context.getSystemService(RoleManager::class.java)
    var isDefault by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    ScreenScaffold("Settings", onBack) {
        androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!isDefault && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                ListRow("Make Driftwood your default browser", "Links from other apps open here", Tabler.DeviceMobile, onClick = {
                    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                })
            }

            SectionHeader("Search engine")
            SearchEngine.entries.forEach { engine ->
                Choice(engine.label, settings.searchEngine == engine) { app.settings.update { it.copy(searchEngine = engine) } }
            }

            SectionHeader("Address bar")
            Choice("Bottom (hides when scrolling down)", settings.barPosition == BarPosition.BOTTOM, Tabler.LayoutBottombar) {
                app.settings.update { it.copy(barPosition = BarPosition.BOTTOM) }
            }
            Choice("Top (hides when scrolling up)", settings.barPosition == BarPosition.TOP, Tabler.LayoutNavbar) {
                app.settings.update { it.copy(barPosition = BarPosition.TOP) }
            }

            SectionHeader("Links")
            ListRow("Open links in apps", "Links to Reddit, YouTube and others open in their app if it's installed", Tabler.ExternalLink, onClick = {
                app.settings.update { it.copy(openLinksInApps = !it.openLinksInApps) }
            }) {
                Switch(
                    checked = settings.openLinksInApps,
                    onCheckedChange = { on -> app.settings.update { it.copy(openLinksInApps = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent),
                )
            }

            SectionHeader("Theme")
            ListRow("Follow phone's light/dark mode", "Pick a light and a dark theme below", Tabler.Palette, onClick = {
                app.settings.update { it.copy(followSystem = !it.followSystem) }
            }) {
                Switch(
                    checked = settings.followSystem,
                    onCheckedChange = { on -> app.settings.update { it.copy(followSystem = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent),
                )
            }
            if (settings.followSystem) {
                Label("When the phone is in light mode")
                ThemeId.entries.filter { !it.palette.isDark }.forEach { theme ->
                    ThemeChoice(theme, settings.lightTheme == theme) { app.settings.update { it.copy(lightTheme = theme) } }
                }
                Label("When the phone is in dark mode")
                ThemeId.entries.filter { it.palette.isDark }.forEach { theme ->
                    ThemeChoice(theme, settings.darkTheme == theme) { app.settings.update { it.copy(darkTheme = theme) } }
                }
            } else {
                ThemeId.entries.forEach { theme ->
                    ThemeChoice(theme, settings.theme == theme) { app.settings.update { it.copy(theme = theme) } }
                }
            }

            SectionHeader("Privacy & ad blocking")
            ListRow("Ad blocking", "Turn it on or off, and see what was blocked", Tabler.ShieldCheck, onClick = { navigate(Screen.ADBLOCK) })
            ListRow("Extensions", "uBlock Origin and other Firefox add-ons", Tabler.Puzzle, onClick = { navigate(Screen.EXTENSIONS) })
            ListRow("Clear cookies and site data", "Signs you out of websites", Tabler.Trash, onClick = {
                app.prompts.confirm("Clear cookies and site data?", "You'll be signed out of most websites.", "Clear") {
                    app.runtime.storageController.clearData(
                        StorageController.ClearFlags.COOKIES or
                            StorageController.ClearFlags.SITE_DATA or
                            StorageController.ClearFlags.ALL_CACHES,
                    )
                    app.prompts.toast("Cleared")
                }
            })

            SectionHeader("Passwords")
            ListRow("Saved passwords", "Fingerprint or PIN required to view", Tabler.Key, onClick = { navigate(Screen.PASSWORDS) })
            ListRow("Import passwords from Chrome", "Choose the CSV file Chrome exported", Tabler.FileImport, onClick = importPasswords)

            SectionHeader("Bookmarks")
            ListRow("Import bookmarks from Chrome", "Choose the HTML file Chrome exported", Tabler.FileImport, onClick = importBookmarks)

            SectionHeader("About")
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            ListRow("Driftwood ${version.orEmpty()}", "Built on GeckoView, Firefox's engine", Tabler.InfoCircle)
            ListRow("Open-source licences", null, Tabler.File, onClick = { showLicences = true })
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLicences) {
        AlertDialog(
            onDismissRequest = { showLicences = false },
            confirmButton = { TextButton(onClick = { showLicences = false }) { Text("Close") } },
            title = { Text("Licences") },
            text = {
                Text(
                    "GeckoView: Mozilla Public License 2.0\n\n" +
                        "Montserrat font: SIL Open Font License 1.1 (The Montserrat Project Authors)\n\n" +
                        "Tabler Icons: MIT License (Paweł Kuna)\n\n" +
                        "uBlock Origin is installed from addons.mozilla.org: GPLv3 (Raymond Hill)\n\n" +
                        "Gruvbox colours by Pavel Pertsev; Rosé Pine colours by the Rosé Pine team.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalPalette.current.subtle,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    ListRow(label, null, icon, onClick = onClick) {
        if (selected) TIcon(Tabler.Check, "Selected", tint = palette.accent)
    }
}

/** A theme option with a little preview of its background and accent colour. */
@Composable
private fun ThemeChoice(theme: ThemeId, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    ListRow(theme.label, null, null, onClick = onClick) {
        Row {
            Swatch(theme.palette.background)
            Swatch(theme.palette.surfaceHigh)
            Swatch(theme.palette.text)
            Swatch(theme.palette.accent)
        }
        Box(Modifier.size(32.dp).padding(start = 8.dp)) {
            if (selected) TIcon(Tabler.Check, "Selected", tint = palette.accent)
        }
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, LocalPalette.current.surfaceHigh, CircleShape),
    )
}
