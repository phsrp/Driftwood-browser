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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.engine.ExtensionManager
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette
import java.text.NumberFormat

/** How many ads and trackers were blocked: overall and on the current page. */
@Composable
fun StatsScreen(app: DriftwoodApp, onBack: () -> Unit) {
    val stats = app.blockStats
    val palette = LocalPalette.current
    val installed by app.extensions.installed.collectAsState()
    val ublockActive = installed.any { it.id == ExtensionManager.UBLOCK_ID && it.metaData.enabled }
    val tab = app.tabs.current
    val n = NumberFormat.getIntegerInstance()

    ScreenScaffold("Blocking stats", onBack, actions = {
        TextButton(onClick = {
            app.prompts.confirm("Reset stats?", "All counters go back to zero.", "Reset") { stats.reset() }
        }) { Text("Reset") }
    }) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Big total
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

            Card {
                Text("uBlock Origin", style = MaterialTheme.typography.titleMedium)
                Text("Ads, trackers and other junk requests", style = MaterialTheme.typography.bodySmall, color = palette.subtle)
                Spacer(Modifier.height(8.dp))
                StatRow("Blocked", stats.ublockTotal)
                if (!ublockActive) {
                    Text(
                        "uBlock Origin isn't active. Check Extensions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger,
                    )
                }
            }

            Card {
                Text("Firefox tracking protection", style = MaterialTheme.typography.titleMedium)
                Text("Built into the engine, works even without extensions", style = MaterialTheme.typography.bodySmall, color = palette.subtle)
                Spacer(Modifier.height(8.dp))
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
                "uBlock Origin's count is read from its toolbar badge on each page, so it matches what uBlock itself reports. " +
                    "Some requests can be blocked by both, so the total may count a few twice.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
            )
        }
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
