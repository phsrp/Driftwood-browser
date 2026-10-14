package com.driftwood.browser.engine

import com.driftwood.browser.DriftwoodApp
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object Engine {
    /** Creates the one Gecko engine the whole app shares. */
    fun createRuntime(app: DriftwoodApp): GeckoRuntime {
        val settings = GeckoRuntimeSettings.Builder()
            // Lets addons.mozilla.org's "Add to Firefox" button install extensions.
            .extensionsWebAPIEnabled(true)
            .loginAutofillEnabled(true)
            .consoleOutput(false)
            .build()
        val runtime = GeckoRuntime.create(app, settings)
        setTrackingProtection(runtime, app.settings.current.trackingProtection)
        runtime.setAutocompleteStorageDelegate(LoginStorage(app))
        return runtime
    }

    /**
     * Firefox's built-in tracking protection: the standard list plus cryptominers and
     * fingerprinters when on, nothing when off. Takes effect for the next page load.
     */
    fun setTrackingProtection(runtime: GeckoRuntime, enabled: Boolean) {
        val blocking = runtime.settings.contentBlocking
        if (enabled) {
            blocking.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            blocking.setAntiTracking(
                ContentBlocking.AntiTracking.DEFAULT or
                    ContentBlocking.AntiTracking.CRYPTOMINING or
                    ContentBlocking.AntiTracking.FINGERPRINTING,
            )
        } else {
            blocking.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE)
            blocking.setAntiTracking(ContentBlocking.AntiTracking.NONE)
        }
    }
}

/**
 * Connects Gecko's login autofill to Driftwood's encrypted password store.
 * Gecko asks for logins when it finds a login form, and offers to save new ones.
 */
private class LoginStorage(private val app: DriftwoodApp) : Autocomplete.StorageDelegate {
    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>>? {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        val openHosts = app.tabs.tabs.mapNotNull { com.driftwood.browser.autofill.DomainMatcher.host(it.url) }.toSet()
        app.scope.launch {
            val logins = runCatching { app.passwords.candidates(domain, openHosts) }.getOrDefault(emptyList())
            result.complete(
                logins.map {
                    Autocomplete.LoginEntry.Builder()
                        .guid(it.guid)
                        .origin(it.origin)
                        .formActionOrigin("")
                        .username(it.username)
                        .password(it.password)
                        .build()
                }.toTypedArray(),
            )
        }
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        app.scope.launch {
            runCatching { app.passwords.saveFromBrowser(login.origin, login.username, login.password, login.guid) }
        }
    }
}
