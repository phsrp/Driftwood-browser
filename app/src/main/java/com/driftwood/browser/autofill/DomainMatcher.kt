package com.driftwood.browser.autofill

import java.net.URI

/**
 * Exact-domain rules for filling saved logins. A login saved for
 * "accounts.example.com" fills only on "accounts.example.com": not on
 * "example.com", "www.example.com", or "accounts.example.com.evil.net".
 */
object DomainMatcher {
    /** Lower-case host of a URL, or null if it has none. */
    fun host(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url.trim()).host?.lowercase()?.trimEnd('.') }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** "scheme://host[:port]" for a saved login, or null for non-website entries. */
    fun origin(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    fun matches(savedOrigin: String, pageUrl: String?): Boolean {
        val saved = host(savedOrigin) ?: return false
        val page = host(pageUrl) ?: return false
        return saved == page
    }
}
