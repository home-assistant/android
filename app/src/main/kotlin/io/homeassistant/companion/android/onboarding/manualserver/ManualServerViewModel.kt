package io.homeassistant.companion.android.onboarding.manualserver

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val HTTPS_PREFIX = "https://"
private const val SCHEME_SEPARATOR = "://"

/**
 * A manually entered server address resolved into a connectable URL.
 *
 * @property url The normalized server URL, always carrying an `http` or `https` scheme.
 * @property fallbackToPlainHttp Whether connecting may be retried over plain `http` when `https`
 * fails. Only `true` when the input carried no scheme, so `https` was merely assumed — an explicit
 * `https://` address must never silently downgrade.
 */
internal data class ManualServerUrl(val url: HttpUrl, val fallbackToPlainHttp: Boolean)

@HiltViewModel
internal class ManualServerViewModel @Inject constructor() : ViewModel() {
    private val serverUrlMutableFlow = MutableStateFlow("")
    val serverUrlFlow = serverUrlMutableFlow.asStateFlow()

    private val resolvedUrlMutableFlow = MutableStateFlow<ManualServerUrl?>(null)
    val resolvedUrlFlow = resolvedUrlMutableFlow.asStateFlow()

    /**
     * Updates the entered server address and resolves it to a [ManualServerUrl], `https` being
     * assumed when no `http`/`https` scheme is typed.
     */
    fun onServerUrlChange(url: String) {
        serverUrlMutableFlow.update { url }
        resolvedUrlMutableFlow.update { url.resolveToManualServerUrl() }
    }
}

/**
 * Turns [this] user input into a [ManualServerUrl], or `null` when it cannot become an http(s) URL.
 * Input without a scheme is tried as `https`.
 */
private fun String.resolveToManualServerUrl(): ManualServerUrl? {
    val trimmed = trim()
    val explicitUrl = trimmed.toHttpUrlOrNull()
    return when {
        explicitUrl != null -> ManualServerUrl(explicitUrl, fallbackToPlainHttp = false)
        // Inputs already carrying a scheme — even an unsupported or misspelled one — are not
        // guessed: prepending would turn "ftp://x" into the host "ftp".
        trimmed.contains(SCHEME_SEPARATOR) -> null
        else -> "$HTTPS_PREFIX$trimmed".toHttpUrlOrNull()?.let { ManualServerUrl(it, fallbackToPlainHttp = true) }
    }
}
