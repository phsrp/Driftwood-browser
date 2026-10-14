package com.driftwood.browser.ui.passwords

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.MainActivity
import com.driftwood.browser.data.PasswordRepo
import com.driftwood.browser.data.SavedLogin
import com.driftwood.browser.engine.PromptController
import com.driftwood.browser.importer.ChromePasswordParser
import com.driftwood.browser.security.BiometricGate
import com.driftwood.browser.ui.common.EmptyState
import com.driftwood.browser.ui.common.Field
import com.driftwood.browser.ui.common.IconBtn
import com.driftwood.browser.ui.common.ListRow
import com.driftwood.browser.ui.common.ScreenScaffold
import com.driftwood.browser.ui.common.TIcon
import com.driftwood.browser.ui.icons.Tabler
import com.driftwood.browser.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a picker for Chrome's password CSV, imports it encrypted, then reminds
 * you to delete the CSV (which holds every password in plain text).
 */
@Composable
fun rememberPasswordImporter(app: DriftwoodApp): () -> Unit {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<Pair<PasswordRepo.ImportResult, Uri>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    ChromePasswordParser.parse(text)
                }
            }
            parsed.onFailure {
                app.prompts.toast(it.message ?: "Couldn't read that file")
                return@launch
            }
            val imported = app.passwords.importAll(parsed.getOrThrow())
            result = imported to uri
        }
    }

    result?.let { (summary, uri) ->
        DeleteCsvReminder(app, summary, uri) { result = null }
    }

    return { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
}

@Composable
private fun DeleteCsvReminder(app: DriftwoodApp, summary: PasswordRepo.ImportResult, uri: Uri, onDone: () -> Unit) {
    val palette = LocalPalette.current
    AlertDialog(
        onDismissRequest = {},
        icon = { TIcon(Tabler.AlertTriangle, null, tint = palette.danger, size = 32.dp) },
        title = { Text("Now delete the CSV file") },
        text = {
            Column {
                Text(
                    "Imported ${summary.added} new and updated ${summary.updated} passwords" +
                        (if (summary.skipped > 0) " (${summary.skipped} skipped: duplicates, app logins or empty)." else "."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your passwords are now stored encrypted in Driftwood. But the CSV file you picked still holds " +
                        "every password in plain text, readable by anyone or any app with access to your files. Delete it now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val deleted = runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }.getOrDefault(false)
                if (deleted) {
                    app.prompts.toast("CSV file deleted")
                } else {
                    app.prompts.toast("Couldn't delete it automatically. Please delete it in your Files app.")
                }
                onDone()
            }) { Text("Delete the file") }
        },
        dismissButton = {
            TextButton(onClick = {
                app.prompts.toast("Remember to delete the CSV file!")
                onDone()
            }) { Text("I'll delete it myself") }
        },
    )
}

@Composable
fun PasswordsScreen(activity: MainActivity, app: DriftwoodApp, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importPasswords = rememberPasswordImporter(app)
    val canLock = remember { BiometricGate.isAvailable(context) }

    var unlocked by remember { mutableStateOf(false) }
    var logins by remember { mutableStateOf<List<SavedLogin>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SavedLogin?>(null) }

    fun reload() = scope.launch { logins = app.passwords.all() }

    fun unlock() {
        activity.biometrics.authenticate(
            "View saved passwords",
            onSuccess = {
                unlocked = true
                reload()
            },
            onFailure = { app.prompts.toast(it) },
        )
    }

    // Lock again as soon as the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false
                logins = emptyList()
                selected = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (canLock) unlock() }

    ScreenScaffold("Passwords", onBack, actions = {
        IconBtn(Tabler.FileImport, "Import from Chrome", importPasswords)
    }) {
        when {
            !canLock -> EmptyState(
                Tabler.Lock,
                "Set up a screen lock (fingerprint, PIN, pattern or password) in your phone's settings to view saved passwords.",
            )
            !unlocked -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TIcon(Tabler.Lock, null, tint = palette.muted, size = 44.dp)
                Spacer(Modifier.height(16.dp))
                Text("Passwords are locked", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { unlock() }, shape = RoundedCornerShape(24.dp)) { Text("Unlock") }
            }
            logins.isEmpty() -> EmptyState(
                Tabler.Key,
                "No saved passwords. Import them from Chrome with the button above: in Chrome, open Settings → Password Manager → Settings → Export passwords.",
            )
            else -> {
                Field(query, { query = it }, "Search passwords", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                val filtered = logins.filter { query.isBlank() || it.host.contains(query, true) || it.username.contains(query, true) }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(filtered, key = { it.id }) { login ->
                        ListRow(login.host, login.username.ifEmpty { "(no username)" }, Tabler.Key, onClick = { selected = login })
                    }
                }
            }
        }
    }

    selected?.let { login ->
        LoginDialog(app, login, onDismiss = { selected = null }, onDelete = {
            app.prompts.confirm("Delete this password?", "${login.username} on ${login.host}", "Delete") {
                scope.launch {
                    app.passwords.delete(login.id)
                    selected = null
                    reload()
                }
            }
        })
    }
}

@Composable
private fun LoginDialog(app: DriftwoodApp, login: SavedLogin, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalPalette.current
    var revealed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(login.host) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Fills only on ${login.host}", style = MaterialTheme.typography.bodySmall, color = palette.subtle)
                Spacer(Modifier.height(8.dp))
                ListRow("Username", login.username.ifEmpty { "(none)" }) {
                    IconBtn(Tabler.Copy, "Copy username", {
                        PromptController.copyToClipboard(app, "Username", login.username)
                        app.prompts.toast("Username copied")
                    })
                }
                ListRow("Password", if (revealed) login.password else "••••••••••") {
                    IconBtn(if (revealed) Tabler.EyeOff else Tabler.Eye, if (revealed) "Hide" else "Show", { revealed = !revealed })
                    IconBtn(Tabler.Copy, "Copy password", {
                        PromptController.copyToClipboard(app, "Password", login.password, sensitive = true)
                        app.prompts.toast("Password copied")
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete", color = palette.danger) } },
    )
}
