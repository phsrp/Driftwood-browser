package com.driftwood.browser.ui.stats

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.engine.Engine
import com.driftwood.browser.engine.ExtensionManager
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette
import java.text.NumberFormat

/** Turn ad blocking on or off, and see how much was blocked. */
@Composable
fun AdBlockingScreen(app: DriftwoodApp, onBack: () -> Unit) {
    val stats = app.blockStats
    val palette = LocalPalette.current
    val settings by app.settings.state.collectAsState()
    val installed by app.extensions.installed.collectAsState()
    val ublock = installed.firstOrNull { it.id == ExtensionManager.UBLOCK_ID }
    val tab = app.tabs.current
    val n = NumberFormat.getIntegerInstance()
    LaunchedEffect(Unit) { app.extensions.refresh() }

    // Changes apply from the next page load, so reload the page you're on.
    fun reloadPage() {
        tab?.takeIf { !it.showHome }?.session?.reload()
    }

    ScreenScaffold("Ad blocking", onBack, actions = {
        TextButton(onClick = {
            app.prompts.confirm("Reset the counters?", "All blocked counts go back to zero.", "Reset") { stats.reset() }
        }) { Text("Reset") }
    }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Total
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TIcon(Tabler.ShieldCheck, null, tint = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Blocked in total", style = MaterialTheme.typography.titleSmall, color = palette.subtle)
                }
                Text(
                    n.format(stats.ublockTotal + stats.trackerTotal),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                )
                Text(
                    "since " + DateFormat.getMediumDateFormat(app).format(stats.since),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.subtle,
                )
            }

            // uBlock Origin
            Card {
                ToggleHeader(
                    title = "uBlock Origin",
                    subtitle = "Blocks ads, trackers and other junk",
                    checked = ublock?.metaData?.enabled == true,
                    enabled = ublock != null,
                    onChange = { on ->
                        ublock?.let { app.extensions.setEnabled(it, on) }
                        reloadPage()
                    },
                )
                if (ublock == null) {
                    Text(
                        "uBlock Origin isn't installed yet. It installs automatically when you're online, or tap below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                    )
                    TextButton(onClick = { app.extensions.install(ExtensionManager.UBLOCK_URL) }) { Text("Install uBlock Origin") }
                }
                Spacer(Modifier.height(4.dp))
                StatRow("Blocked", stats.ublockTotal)
            }

            // Firefox tracking protection
            Card {
                ToggleHeader(
                    title = "Firefox tracking protection",
                    subtitle = "Built into the engine, works even without extensions",
                    checked = settings.trackingProtection,
                    onChange = { on ->
                        app.settings.update { it.copy(trackingProtection = on) }
                        Engine.setTrackingProtection(app.runtime, on)
                        reloadPage()
                    },
                )
                Spacer(Modifier.height(4.dp))
                StatRow("Tracking content & ads", stats.trackers)
                StatRow("Social media trackers", stats.social)
                StatRow("Fingerprinters", stats.fingerprinters)
                StatRow("Cryptominers", stats.cryptominers)
            }

            if (tab != null && !tab.showHome) {
                Card {
                    Text("This page", style = MaterialTheme.typography.titleMedium)
                    Text(UrlInput.display(tab.url), style = MaterialTheme.typography.bodySmall, color = palette.subtle)
                    Spacer(Modifier.height(8.dp))
                    StatRow("By uBlock Origin", tab.ublockBlocked.toLong())
                    StatRow("By tracking protection", tab.trackersBlocked.toLong())
                }
            }

            Text(
                "Turning either one off takes effect when the page reloads. uBlock Origin's count is read from its own " +
                    "counter on each page. Some requests can be blocked by both, so the total may count a few twice.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun ToggleHeader(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.subtle)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent, checkedThumbColor = palette.onAccent),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LocalPalette.current.surface)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun StatRow(label: String, value: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            NumberFormat.getIntegerInstance().format(value),
            style = MaterialTheme.typography.titleMedium,
            color = LocalPalette.current.accent,
        )
    }
}
