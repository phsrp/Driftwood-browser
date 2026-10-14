package com.driftwood.browser.engine

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.tabs.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Saves files into the phone's Downloads folder and lists them in Driftwood. */
class Downloader(private val app: DriftwoodApp) {

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

        val mime = contentType ?: guessMime(url)
        val name = URLUtil.guessFileName(url, header("content-disposition"), mime)
        val body = response.body
        if (body == null) {
            download(url, tab.isPrivate, name)
            return
        }
        app.prompts.toast("Downloading $name…")
        app.scope.launch { save(name, mime, body, tab.isPrivate) }
    }

    /** Downloads a URL directly (long-press "Download link" / "Save image"). */
    fun download(url: String, isPrivate: Boolean, knownName: String? = null) {
        app.prompts.toast("Downloading…")
        app.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    val mime = connection.contentType?.substringBefore(';')?.trim() ?: guessMime(url)
                    val name = knownName ?: URLUtil.guessFileName(url, connection.getHeaderField("Content-Disposition"), mime)
                    Triple(name, mime, connection.inputStream)
                }.getOrNull()
            }
            if (ok == null) {
                app.prompts.toast("Download failed")
                return@launch
            }
            save(ok.first, ok.second, ok.third, isPrivate)
        }
    }

    private suspend fun save(name: String, mime: String, input: InputStream, isPrivate: Boolean) {
        val resolver = app.contentResolver
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Couldn't create the file")
                val size = input.use { stream ->
                    resolver.openOutputStream(item)!!.use { out -> stream.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(item, values, null, null)
                item to size
            }.getOrNull()
        }
        if (saved == null) {
            app.prompts.toast("Download failed: $name")
            return
        }
        // Incognito downloads are saved, but not listed in Driftwood's download history.
        if (!isPrivate) app.downloads.add(name, saved.first.toString(), mime, saved.second)
        app.prompts.toast("Saved to Downloads: $name")
    }

    fun open(uri: String, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            app.prompts.toast("No app can open this file")
        }
    }

    private fun guessMime(url: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
            ?: "application/octet-stream"
}
