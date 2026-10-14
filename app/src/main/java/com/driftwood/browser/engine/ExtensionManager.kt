package com.driftwood.browser.engine

import android.util.Log
import com.driftwood.browser.DriftwoodApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Firefox extensions. uBlock Origin is installed from addons.mozilla.org the first
 * time the app starts, and any other extension can be added later.
 */
class ExtensionManager(private val app: DriftwoodApp) {
    private val controller: WebExtensionController get() = app.runtime.webExtensionController

    private val _installed = MutableStateFlow<List<WebExtension>>(emptyList())
    val installed: StateFlow<List<WebExtension>> get() = _installed

    /** Extensions the app installs itself, so no permission dialog is shown for them. */
    private val autoAccept = mutableSetOf<String>()
    private var started = false

    fun start() {
        if (started) return
        started = true
        controller.setPromptDelegate(promptDelegate)
        refresh()
        installUblockOnce()
        checkForUpdates()
    }

    fun refresh() {
        controller.list().accept({ list ->
            _installed.value = list.orEmpty().filter { !it.isBuiltIn }
            list.orEmpty().firstOrNull { it.id == UBLOCK_ID }?.let { app.blockStats.onUblockAvailable(it) }
        }, { log(it) })
    }

    /**
     * Installs from an .xpi link or an addons.mozilla.org page link,
     * e.g. https://addons.mozilla.org/firefox/addon/darkreader/
     */
    fun install(input: String) {
        val url = toInstallUrl(input.trim())
        if (url == null) {
            app.prompts.toast("That's not an extension link")
            return
        }
        app.prompts.toast("Installing extension…")
        controller.install(url).accept({ ext ->
            refresh()
            ext?.let { app.prompts.toast("Installed ${it.metaData.name ?: "extension"}") }
        }, { error ->
            log(error)
            // Cancelling the permission dialog also lands here.
            app.prompts.toast("Extension not installed")
        })
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        val result = if (enabled) {
            controller.enable(extension, WebExtensionController.EnableSource.USER)
        } else {
            controller.disable(extension, WebExtensionController.EnableSource.USER)
        }
        result.accept({ refresh() }, { log(it) })
    }

    fun setAllowedInPrivate(extension: WebExtension, allowed: Boolean) {
        controller.setAllowedInPrivateBrowsing(extension, allowed).accept({ refresh() }, { log(it) })
    }

    fun uninstall(extension: WebExtension) {
        controller.uninstall(extension).accept({ refresh() }, { log(it) })
    }

    private fun installUblockOnce() {
        val flags = app.settings.internal
        if (flags.getBoolean("ublock_installed", false)) return
        controller.list().accept({ list ->
            if (list.orEmpty().any { it.id == UBLOCK_ID }) {
                flags.edit().putBoolean("ublock_installed", true).apply()
                return@accept
            }
            autoAccept += UBLOCK_ID
            controller.install(UBLOCK_URL).accept({ ext ->
                flags.edit().putBoolean("ublock_installed", true).apply()
                if (ext != null) controller.setAllowedInPrivateBrowsing(ext, true)
                refresh()
            }, { error ->
                // Probably offline. It's tried again on the next launch.
                log(error)
            })
        }, { log(it) })
    }

    /** Gecko doesn't update extensions by itself; check once a day. */
    private fun checkForUpdates() {
        val flags = app.settings.internal
        val now = System.currentTimeMillis()
        if (now - flags.getLong("extensions_checked", 0) < 24 * 60 * 60 * 1000L) return
        flags.edit().putLong("extensions_checked", now).apply()
        controller.list().accept({ list ->
            list.orEmpty().filter { !it.isBuiltIn }.forEach { ext ->
                controller.update(ext).accept({ refresh() }, { log(it) })
            }
        }, { log(it) })
    }

    private val promptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            if (extension.id in autoAccept) {
                return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))
            }
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            val wants = (permissions.toList() + origins.toList()).map(::describePermission).distinct()
            app.prompts.push(PromptUi.ExtensionInstall(extension.metaData.name ?: extension.id, wants) { allow, private ->
                result.complete(WebExtension.PermissionPromptResponse(allow, private, false))
            })
            return result
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<String>,
            newOrigins: Array<String>,
            newDataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny>? {
            val result = GeckoResult<AllowOrDeny>()
            val wants = (newPermissions.toList() + newOrigins.toList()).map(::describePermission).distinct()
            app.prompts.push(
                PromptUi.Confirm(
                    "Update ${extension.metaData.name ?: "extension"}?",
                    "The new version also wants to:\n" + wants.joinToString("\n") { "• $it" },
                    "Update",
                ) { result.complete(if (it) AllowOrDeny.ALLOW else AllowOrDeny.DENY) },
            )
            return result
        }

        override fun onOptionalPrompt(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny>? {
            val result = GeckoResult<AllowOrDeny>()
            val wants = (permissions.toList() + origins.toList()).map(::describePermission).distinct()
            app.prompts.push(
                PromptUi.Confirm(
                    "${extension.metaData.name ?: "Extension"} wants more access",
                    wants.joinToString("\n") { "• $it" },
                    "Allow",
                ) { result.complete(if (it) AllowOrDeny.ALLOW else AllowOrDeny.DENY) },
            )
            return result
        }
    }

    private fun describePermission(p: String): String = when (p) {
        "<all_urls>", "*://*/*", "http://*/*", "https://*/*" -> "Access your data on all websites"
        "tabs" -> "Access browser tabs"
        "storage", "unlimitedStorage" -> "Store data on your device"
        "webRequest", "webRequestBlocking" -> "Block or change web requests"
        "privacy" -> "Read and change privacy settings"
        "downloads" -> "Manage downloads"
        "history" -> "Access browsing history"
        "bookmarks" -> "Read and change bookmarks"
        "notifications" -> "Show notifications"
        "cookies" -> "Access cookies"
        "dns" -> "Look up website addresses"
        "webNavigation" -> "Access browser activity during navigation"
        "contextMenus", "menus", "activeTab", "alarms", "scripting" -> "Change how pages work"
        else -> p
    }

    private fun toInstallUrl(input: String): String? {
        if (input.isEmpty()) return null
        if (input.substringBefore('?').endsWith(".xpi")) return input
        val slug = Regex("""addons\.mozilla\.org/(?:[^/]+/)*addon/([^/?#]+)""").find(input)?.groupValues?.get(1)
        return slug?.let { "https://addons.mozilla.org/firefox/downloads/latest/$it/latest.xpi" }
    }

    private fun log(error: Throwable?) {
        Log.w("Extensions", "Extension operation failed", error)
    }

    companion object {
        const val UBLOCK_ID = "uBlock0@raymondhill.net"
        const val UBLOCK_URL = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        const val ADDONS_PAGE = "https://addons.mozilla.org/android/"
    }
}
