package com.driftwood.browser.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette

@Composable
fun TIcon(icon: ImageVector, description: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 22.dp) {
    Icon(icon, description, modifier.size(size), tint = tint)
}

@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurface) {
    IconButton(onClick = onClick, enabled = enabled) {
        TIcon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

/** A full-screen page with a back arrow and title, used for every screen except the browser. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Tabler.ArrowLeft, "Back", onBack)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val base = Modifier.fillMaxWidth()
    val clickable = if (onClick != null || onLongClick != null) {
        base.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else base
    Row(
        clickable.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TIcon(icon, null, tint = iconTint)
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TIcon(icon, null, tint = LocalPalette.current.muted, size = 44.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = LocalPalette.current.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Rounded text field in the app's style. */
@Composable
fun Field(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** Dialog with a title field and a URL field (bookmarks, shortcuts). */
@Composable
fun TitleUrlDialog(
    heading: String,
    initialTitle: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (title: String, url: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(title, { title = it }, "Name")
                Field(url, { url = it }, "Address")
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim().ifEmpty { url.trim() }, url.trim()) }, enabled = url.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Round letter badge used instead of website icons. */
@Composable
fun LetterBadge(text: String, size: Dp = 52.dp) {
    val palette = LocalPalette.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(palette.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = palette.accent,
        )
    }
}
