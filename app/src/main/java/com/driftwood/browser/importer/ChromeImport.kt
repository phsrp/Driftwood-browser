package com.driftwood.browser.importer

import android.text.Html
import com.driftwood.browser.data.NewLogin

/**
 * Reads Chrome's "Export bookmarks" HTML file (the old Netscape bookmark format).
 * Returns (title, url, folder) triples. Folder is a path like "Bookmarks bar / Work".
 */
object ChromeBookmarkParser {
    private val token = Regex(
        """<H3[^>]*>(.*?)</H3>|<A\s[^>]*?HREF="([^"]*)"[^>]*>(.*?)</A>|<DL>|</DL>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(html: String): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        val folders = ArrayList<String?>()
        var pendingFolder: String? = null
        for (match in token.findAll(html)) {
            val text = match.value
            when {
                text.startsWith("<H3", ignoreCase = true) -> pendingFolder = decode(match.groupValues[1])
                text.equals("<DL>", ignoreCase = true) -> {
                    folders += pendingFolder
                    pendingFolder = null
                }
                text.equals("</DL>", ignoreCase = true) -> if (folders.isNotEmpty()) folders.removeAt(folders.lastIndex)
                else -> {
                    val url = decode(match.groupValues[2]).trim()
                    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) continue
                    val title = decode(match.groupValues[3]).trim().ifEmpty { url }
                    out += Triple(title, url, folders.filterNotNull().joinToString(" / "))
                }
            }
        }
        return out
    }

    private fun decode(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
}

/**
 * Reads Chrome's "Export passwords" CSV file. Chrome writes the columns
 * name,url,username,password,note, but the header is read so the order doesn't matter.
 */
object ChromePasswordParser {
    class InvalidFile(message: String) : Exception(message)

    fun parse(csv: String): List<NewLogin> {
        val rows = parseCsv(csv.removePrefix("﻿"))
        if (rows.isEmpty()) throw InvalidFile("The file is empty.")
        val header = rows.first().map { it.trim().lowercase() }
        val urlCol = header.indexOf("url")
        val userCol = header.indexOf("username")
        val passCol = header.indexOf("password")
        if (urlCol < 0 || passCol < 0) {
            throw InvalidFile("This doesn't look like a Chrome password export (no url/password columns).")
        }
        return rows.drop(1).filter { it.size > maxOf(urlCol, passCol) }.map { row ->
            NewLogin(
                url = row[urlCol].trim(),
                username = if (userCol >= 0 && userCol < row.size) row[userCol] else "",
                password = row[passCol],
            )
        }
    }

    /** Standard CSV: commas, double-quoted fields, "" for a quote, newlines inside quotes. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { row += field.toString(); field.clear() }
                    '\r' -> Unit
                    '\n' -> {
                        row += field.toString(); field.clear()
                        if (row.any { it.isNotEmpty() }) rows += row
                        row = ArrayList()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotEmpty() }) rows += row
        return rows
    }
}
