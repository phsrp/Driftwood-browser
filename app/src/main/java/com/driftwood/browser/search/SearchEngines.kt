package com.driftwood.browser.search

import java.net.URLEncoder

enum class SearchEngine(val label: String, private val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s");

    fun searchUrl(query: String): String = template.replace("%s", URLEncoder.encode(query, "UTF-8"))
}

/** Turns whatever was typed in the address bar into a URL to load. */
object UrlInput {
    private val knownSchemes = listOf("http:", "https:", "about:", "file:", "data:", "moz-extension:", "view-source:")
    private val domainLike = Regex("""^[^\s/]+\.[a-z]{2,}(:\d+)?([/?#].*)?$""", RegexOption.IGNORE_CASE)
    private val localLike = Regex("""^(localhost|\d{1,3}(\.\d{1,3}){3})(:\d+)?([/?#].*)?$""", RegexOption.IGNORE_CASE)

    fun toUrl(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return ""
        val lower = text.lowercase()
        return when {
            knownSchemes.any { lower.startsWith(it) } -> text
            text.contains(' ') -> engine.searchUrl(text)
            domainLike.matches(text) -> "https://$text"
            localLike.matches(text) -> "http://$text"
            else -> engine.searchUrl(text)
        }
    }

    /** Short, readable form of a URL for the address bar (host without "www."). */
    fun display(url: String): String {
        if (url.isBlank()) return ""
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        return host?.removePrefix("www.") ?: url
    }
}
