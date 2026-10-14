package com.driftwood.browser.engine

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.tabs.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** A download in progress (or just finished), shown in the download bar. */
class ActiveDownload(val name: String, val mime: String, val total: Long) {
    enum class State { RUNNING, DONE, FAILED, CANCELLED }

    var received by mutableLongStateOf(0L)
    var state by mutableStateOf(State.RUNNING)
    var uri: String? = null

    /** 0..1, or null when the server didn't say how big the file is. */
    val fraction: Float? get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null

    fun cancel() {
        if (state == State.RUNNING) state = State.CANCELLED
    }
}

/** Saves files into the phone's Downloads folder and lists them in Driftwood. */
class Downloader(private val app: DriftwoodApp) {
    /** Downloads shown in the download bar, newest last. */
    val active = mutableStateListOf<ActiveDownload>()

    /** Gecko decided a response is a file, not a page. */
    fun handle(tab: Tab, response: WebResponse) {
        val header = { name: String -> response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value }
        val contentType = header("content-type")?.substringBefore(';')?.trim()?.lowercase()
        val url = response.uri

        // A Firefox extension file: offer to install it instead of downloading it.
        if (contentType == "application/x-xpinstall" || url.substringBefore('?').endsWith(".xpi")) {
            runCatching { response.body?.close() }
            app.extensions.install(url)
            return
        }

        val name = fileName(url, header("content-disposition"), contentType)
        val mime = mimeFor(name, contentType)
        val body = response.body
        if (body == null) {
            download(url, tab.isPrivate, name)
            return
        }
        val size = header("content-length")?.toLongOrNull() ?: -1L
        app.scope.launch { save(name, mime, size, body, tab.isPrivate) }
    }

    /** Downloads a URL directly (long-press "Download link" / "Save image"). */
    fun download(url: String, isPrivate: Boolean, knownName: String? = null) {
        app.scope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                    val name = knownName ?: fileName(url, connection.getHeaderField("Content-Disposition"), type)
                    Triple(name, mimeFor(name, type), connection.contentLengthLong to connection.inputStream)
                }.getOrNull()
            }
            if (opened == null) {
                app.prompts.toast("Download failed")
                return@launch
            }
            save(opened.first, opened.second, opened.third.first, opened.third.second, isPrivate)
        }
    }

    private suspend fun save(name: String, mime: String, size: Long, input: InputStream, isPrivate: Boolean) {
        val download = ActiveDownload(name, mime, size)
        active += download
        val resolver = app.contentResolver
        val item = withContext(Dispatchers.IO) {
            var item: Uri? = null
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Couldn't create the file")
                input.use { stream ->
                    resolver.openOutputStream(item!!)!!.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        var shown = 0L
                        while (true) {
                            if (download.state == ActiveDownload.State.CANCELLED) error("Cancelled")
                            val read = stream.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            received += read
                            // Update the bar every 256 KB, not on every small chunk.
                            if (received - shown >= 256 * 1024) {
                                download.received = received
                                shown = received
                            }
                        }
                    }
                }
                download.received = maxOf(download.received, size.coerceAtLeast(0))
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(item!!, values, null, null)
                item
            }.getOrElse {
                // Remove the half-written file.
                item?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
                null
            }
        }
        if (item == null) {
            if (download.state != ActiveDownload.State.CANCELLED) {
                download.state = ActiveDownload.State.FAILED
                app.prompts.toast("Download failed: $name")
            }
            hideLater(download)
            return
        }
        download.uri = item.toString()
        download.state = ActiveDownload.State.DONE
        // Incognito downloads are saved, but not listed in Driftwood's download history.
        if (!isPrivate) app.downloads.add(name, item.toString(), mime, download.received)
        hideLater(download, 15_000)
    }

    private fun hideLater(download: ActiveDownload, afterMs: Long = 4_000) {
        app.scope.launch {
            delay(afterMs)
            active.remove(download)
        }
    }

    fun dismiss(download: ActiveDownload) {
        download.cancel()
        active.remove(download)
    }

    /** Opens a downloaded file. App files (.apk) go to Android's installer. */
    fun open(uri: String, mime: String, name: String) {
        val type = mimeFor(name, mime)
        if (type == APK && !app.packageManager.canRequestPackageInstalls()) {
            // Android asks once per app: "Allow from this source".
            app.prompts.confirm(
                "Allow installing apps",
                "To install this app, turn on \"Allow from this source\" for Driftwood on the next screen, then come back and tap Open again.",
                "Continue",
            ) {
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(settings) }
            }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            app.prompts.toast("No app can open this file")
        }
    }

    /** File name from the server's Content-Disposition header, else from the URL. */
    private fun fileName(url: String, contentDisposition: String?, contentType: String?): String {
        contentDisposition?.let { cd ->
            Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.let {
                return clean(runCatching { URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrDefault(it.groupValues[1]))
            }
            Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(cd)?.let {
                return clean(it.groupValues[1])
            }
        }
        // Don't pass a generic type: Android would rename "app.apk" to "app.bin".
        val usefulType = contentType?.takeIf { it != "application/octet-stream" }
        return clean(URLUtil.guessFileName(url, null, usefulType))
    }

    private fun clean(name: String) = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").ifEmpty { "download" }

    /** The file's real type, preferring its extension when the server only says "binary data". */
    private fun mimeFor(name: String, contentType: String?): String {
        val fromName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        if (name.endsWith(".apk", ignoreCase = true)) return APK
        return when {
            contentType.isNullOrBlank() || contentType == "application/octet-stream" -> fromName ?: "application/octet-stream"
            else -> contentType
        }
    }

    private companion object {
        const val APK = "application/vnd.android.package-archive"
    }
}
