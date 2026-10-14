package com.driftwood.browser.engine

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.driftwood.browser.DriftwoodApp
import com.driftwood.browser.autofill.DomainMatcher
import com.driftwood.browser.tabs.Tab
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/** A dialog the UI should show. The UI calls [PromptController.finish] when it's answered. */
sealed class PromptUi {
    class Alert(val title: String?, val message: String?, val onDone: () -> Unit) : PromptUi()

    class Confirm(
        val title: String?,
        val message: String?,
        val positive: String = "OK",
        val negative: String = "Cancel",
        val onResult: (Boolean) -> Unit,
    ) : PromptUi()

    class TextInput(val title: String?, val message: String?, val default: String, val onResult: (String?) -> Unit) : PromptUi()

    class Choice(val title: String?, val items: List<ChoiceItem>, val multiple: Boolean, val onResult: (List<String>?) -> Unit) : PromptUi()

    class SaveLogin(val host: String, val username: String, val onResult: (Boolean) -> Unit) : PromptUi()

    class SelectLogin(val host: String, val usernames: List<String>, val onResult: (Int?) -> Unit) : PromptUi()

    class ExtensionInstall(
        val name: String,
        val permissions: List<String>,
        val onResult: (allow: Boolean, allowInPrivate: Boolean) -> Unit,
    ) : PromptUi()

    /** Long-press on a link or image. */
    class ContextMenu(val tab: Tab, val link: String?, val image: String?, val label: String?) : PromptUi()
}

data class ChoiceItem(val id: String, val label: String, val selected: Boolean, val enabled: Boolean, val isHeader: Boolean)

/** A website asked to pick files to upload. */
class FileRequest(val mimeTypes: List<String>, val multiple: Boolean, val complete: (List<Uri>) -> Unit)

class PromptController(private val app: DriftwoodApp) {
    /** Dialogs waiting to be shown; the first one is on screen. */
    val queue = mutableStateListOf<PromptUi>()
    var fileRequest by mutableStateOf<FileRequest?>(null)

    fun push(prompt: PromptUi) {
        queue += prompt
    }

    /** Removes [prompt] from the screen and runs its answer. */
    fun finish(prompt: PromptUi, answer: () -> Unit = {}) {
        queue.remove(prompt)
        answer()
    }

    fun toast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }

    fun confirm(title: String, message: String, positive: String = "OK", onYes: () -> Unit) {
        push(PromptUi.Confirm(title, message, positive) { if (it) onYes() })
    }

    fun delegateFor(tab: Tab): PromptDelegate = object : PromptDelegate {
        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            push(PromptUi.Alert(prompt.title, prompt.message) { result.complete(prompt.dismiss()) })
            return result
        }

        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            push(PromptUi.Confirm(prompt.title, prompt.message) { ok ->
                result.complete(
                    prompt.confirm(
                        if (ok) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE,
                    ),
                )
            })
            return result
        }

        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            push(PromptUi.TextInput(prompt.title, prompt.message, prompt.defaultValue.orEmpty()) { text ->
                result.complete(if (text == null) prompt.dismiss() else prompt.confirm(text))
            })
            return result
        }

        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
            push(PromptUi.Choice(prompt.title ?: prompt.message, flatten(prompt.choices), multiple) { ids ->
                result.complete(
                    when {
                        ids == null -> prompt.dismiss()
                        multiple -> prompt.confirm(ids.toTypedArray())
                        ids.isEmpty() -> prompt.dismiss()
                        else -> prompt.confirm(ids.first())
                    },
                )
            })
            return result
        }

        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            val mimes = prompt.mimeTypes?.filterNotNull()?.filter { it.contains('/') }.orEmpty()
            fileRequest = FileRequest(mimes, prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE) { uris ->
                fileRequest = null
                result.complete(if (uris.isEmpty()) prompt.dismiss() else prompt.confirm(app, uris.toTypedArray()))
            }
            return result
        }

        override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            push(PromptUi.Confirm("Pop-up blocked", "This site wants to open a pop-up window.", "Allow", "Block") { ok ->
                result.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
            })
            return result
        }

        override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            push(PromptUi.Confirm("Leave page?", "Changes you made may not be saved.", "Leave", "Stay") { ok ->
                result.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
            })
            return result
        }

        override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptResponse>? {
            val result = GeckoResult<PromptResponse>()
            push(PromptUi.Confirm("Resend form?", "This page needs to send the form data again.", "Resend") { ok ->
                result.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
            })
            return result
        }

        override fun onLoginSave(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>,
        ): GeckoResult<PromptResponse>? {
            val option = request.options.firstOrNull()
            // Never offer to save passwords typed in incognito tabs.
            if (tab.isPrivate || option == null) return GeckoResult.fromValue(request.dismiss())
            val result = GeckoResult<PromptResponse>()
            val host = DomainMatcher.host(option.value.origin) ?: DomainMatcher.host(tab.url).orEmpty()
            push(PromptUi.SaveLogin(host, option.value.username) { save ->
                result.complete(if (save) request.confirm(option) else request.dismiss())
            })
            return result
        }

        override fun onLoginSelect(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>,
        ): GeckoResult<PromptResponse>? {
            // Exact-domain rule: only offer logins saved for exactly this page's host.
            val allowed = request.options.filter { DomainMatcher.matches(it.value.origin, tab.url) }
            if (allowed.isEmpty()) return GeckoResult.fromValue(request.dismiss())
            val result = GeckoResult<PromptResponse>()
            val host = DomainMatcher.host(tab.url).orEmpty()
            push(PromptUi.SelectLogin(host, allowed.map { it.value.username.ifEmpty { "(no username)" } }) { index ->
                result.complete(if (index == null) request.dismiss() else request.confirm(allowed[index]))
            })
            return result
        }
    }

    private fun flatten(choices: Array<PromptDelegate.ChoicePrompt.Choice>): List<ChoiceItem> {
        val out = ArrayList<ChoiceItem>()
        for (choice in choices) {
            if (choice.separator) continue
            val children = choice.items
            if (children != null) {
                out += ChoiceItem(choice.id, choice.label, false, false, isHeader = true)
                out += flatten(children)
            } else {
                out += ChoiceItem(choice.id, choice.label, choice.selected, !choice.disabled, isHeader = false)
            }
        }
        return out
    }

    companion object {
        fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean = false) {
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            val clip = android.content.ClipData.newPlainText(label, text)
            if (sensitive) {
                clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            clipboard.setPrimaryClip(clip)
        }
    }
}
