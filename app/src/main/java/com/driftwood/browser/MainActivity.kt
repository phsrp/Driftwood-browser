package com.driftwood.browser

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.driftwood.browser.search.UrlInput
import com.driftwood.browser.security.BiometricGate
import com.driftwood.browser.ui.DriftwoodRoot

class MainActivity : FragmentActivity() {
    lateinit var biometrics: BiometricGate
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        biometrics = BiometricGate(this)

        app.tabs.restoreIfNeeded()
        app.extensions.start()
        if (savedInstanceState == null) handleIntent(intent)

        setContent { DriftwoodRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        app.tabs.persist()
    }

    /** Links opened from other apps, and web searches from the system. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { app.tabs.openExternalLink(it) }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let {
                app.tabs.openExternalLink(UrlInput.toUrl(it, app.settings.current.searchEngine))
            }
        }
    }
}
