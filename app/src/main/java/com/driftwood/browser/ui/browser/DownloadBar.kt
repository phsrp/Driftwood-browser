package com.driftwood.browser.ui.browser

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.engine.ActiveDownload
import com.driftwood.browser.ui.common.IconBtn
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette

/** Floating cards above the page showing each download's progress. */
@Composable
fun DownloadBar(app: DriftwoodApp, modifier: Modifier = Modifier) {
    val downloads = app.downloader.active
    if (downloads.isEmpty()) return
    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        downloads.takeLast(3).forEach { DownloadCard(app, it) }
    }
}

@Composable
private fun DownloadCard(app: DriftwoodApp, download: ActiveDownload) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    val (icon, tint) = when (download.state) {
        ActiveDownload.State.RUNNING -> Tabler.Download to palette.accent
        ActiveDownload.State.DONE -> Tabler.Check to palette.accent
        else -> Tabler.AlertTriangle to palette.danger
    }
    val status = when (download.state) {
        ActiveDownload.State.RUNNING -> download.fraction?.let {
            "${(it * 100).toInt()}% · ${size(download.received)} of ${size(download.total)}"
        } ?: "${size(download.received)} downloaded"
        ActiveDownload.State.DONE -> "Done · ${size(download.received)} · saved to Downloads"
        ActiveDownload.State.FAILED -> "Download failed"
        ActiveDownload.State.CANCELLED -> "Cancelled"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TIcon(icon, null, tint = tint, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(download.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodySmall, color = palette.subtle, maxLines = 1)
            }
            if (download.state == ActiveDownload.State.DONE) {
                TextButton(onClick = {
                    download.uri?.let { app.downloader.open(it, download.mime, download.name) }
                }) { Text("Open") }
            }
            IconBtn(
                Tabler.X,
                if (download.state == ActiveDownload.State.RUNNING) "Cancel download" else "Close",
                { app.downloader.dismiss(download) },
                tint = palette.subtle,
            )
        }
        if (download.state == ActiveDownload.State.RUNNING) {
            val fraction = download.fraction
            val barModifier = Modifier.fillMaxWidth().padding(end = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = barModifier,
                    color = palette.accent,
                    trackColor = palette.surfaceHigh,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                // Size unknown: an animated bar instead of a percentage.
                LinearProgressIndicator(modifier = barModifier, color = palette.accent, trackColor = palette.surfaceHigh)
            }
        }
    }
}
