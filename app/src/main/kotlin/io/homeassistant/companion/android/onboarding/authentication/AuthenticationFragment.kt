package io.homeassistant.companion.android.onboarding.authentication

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.themes.NightModeManager
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import io.homeassistant.companion.android.util.isStarted
import javax.inject.Inject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

@AndroidEntryPoint
class AuthenticationFragment : Fragment() {

    companion object {
        private const val AUTH_CALLBACK = "homeassistant://auth-callback"
    }

    private val viewModel by activityViewModels<OnboardingViewModel>()

    private var authUrl: String? = null

    @Inject
    lateinit var nightModeManager: NightModeManager

    @Inject
    @NamedKeyChain
    lateinit var keyChainRepository: KeyChainRepository

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    // TODO once the frontend supports edge to edge we should simply send the insets to the frontend instead of this spacer https://github.com/home-assistant/frontend/pull/25566
                    Spacer(
                        modifier = Modifier.fillMaxWidth().height(
                            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                        )
                            .background(colorResource(commonR.color.colorLaunchScreenBackground)),
                    )

                    var nightModeTheme by remember { mutableStateOf<NightModeTheme?>(null) }

                    LaunchedEffect(Unit) {
                        nightModeTheme = nightModeManager.getCurrentNightMode()
                    }

                    HAWebView(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        nightModeTheme = nightModeTheme,
                        configure = {
                            webViewClient = object : TLSWebViewClient(keyChainRepository) {
                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                                    return onRedirect(url)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.url?.toString() == authUrl) {
                                        Timber.e(
                                            "onReceivedError: Status Code: ${error?.errorCode} Description: ${error?.description}",
                                        )
                                        showError(
                                            requireContext().getString(
                                                commonR.string.error_http_generic,
                                                error?.errorCode,
                                                if (error?.description.isNullOrBlank()) {
                                                    commonR.string.no_description
                                                } else {
                                                    error.description
                                                },
                                            ),
                                            null,
                                            error,
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    if (request?.url?.toString() == authUrl) {
                                        Timber.e(
                                            "onReceivedHttpError: Status Code: ${errorResponse?.statusCode} Description: ${errorResponse?.reasonPhrase}",
                                        )
                                        if (isTLSClientAuthNeeded && !isCertificateChainValid) {
                                            showError(
                                                requireContext().getString(commonR.string.tls_cert_expired_message),
                                                null,
                                                null,
                                            )
                                        } else if (isTLSClientAuthNeeded && errorResponse?.statusCode == 400) {
                                            showError(
                                                requireContext().getString(commonR.string.tls_cert_not_found_message),
                                                null,
                                                null,
                                            )
                                        } else {
                                            showError(
                                                requireContext().getString(
                                                    commonR.string.error_http_generic,
                                                    errorResponse?.statusCode,
                                                    if (errorResponse?.reasonPhrase.isNullOrBlank()) {
                                                        requireContext().getString(commonR.string.no_description)
                                                    } else {
                                                        errorResponse.reasonPhrase
                                                    },
                                                ),
                                                null,
                                                null,
                                            )
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?,
                                ) {
                                    super.onReceivedSslError(view, handler, error)
                                    Timber.e("onReceivedSslError: $error")
                                    showError(requireContext().getString(commonR.string.error_ssl), error, null)
                                }
                            }
                            authUrl = buildAuthUrl(viewModel.manualUrl.value)
                            loadUrl(authUrl!!)
                        },
                    )
                }
            }
        }
    }

    private fun buildAuthUrl(base: String): String {
        return try {
            val url = base.toHttpUrl()
            val builder = if (url.host.endsWith("ui.nabu.casa", true)) {
                HttpUrl.Builder()
                    .scheme(url.scheme)
                    .host(url.host)
                    .port(url.port)
            } else {
                url.newBuilder()
            }
            builder
                .addPathSegments("auth/authorize")
                .addEncodedQueryParameter("response_type", "code")
                .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                .addEncodedQueryParameter("redirect_uri", AUTH_CALLBACK)
                .build()
                .toString()
        } catch (e: Exception) {
            Timber.e(e, "Unable to build authentication URL")
            Toast.makeText(context, commonR.string.error_connection_failed, Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
            ""
        }
    }

    private fun onRedirect(url: String): Boolean {
        val code = url.toUri().getQueryParameter("code")
        return if (url.startsWith(AUTH_CALLBACK) && !code.isNullOrBlank()) {
            viewModel.registerAuthCode(code)
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.content, MobileAppIntegrationFragment::class.java, null)
                .addToBackStack(null)
                .commit()
            true
        } else {
            // The WebViewClient should load this URL
            authUrl = url
            false
        }
    }

    private fun showError(message: String, sslError: SslError?, error: WebResourceError?) {
        if (!isStarted) {
            // Fragment is at least paused, can't display alert
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(commonR.string.error_connection_failed)
            .setMessage(
                when (sslError?.primaryError) {
                    SslError.SSL_DATE_INVALID -> requireContext().getString(
                        commonR.string.webview_error_SSL_DATE_INVALID,
                    )
                    SslError.SSL_EXPIRED -> requireContext().getString(commonR.string.webview_error_SSL_EXPIRED)
                    SslError.SSL_IDMISMATCH -> requireContext().getString(commonR.string.webview_error_SSL_IDMISMATCH)
                    SslError.SSL_INVALID -> requireContext().getString(commonR.string.webview_error_SSL_INVALID)
                    SslError.SSL_NOTYETVALID -> requireContext().getString(commonR.string.webview_error_SSL_NOTYETVALID)
                    SslError.SSL_UNTRUSTED -> requireContext().getString(commonR.string.webview_error_SSL_UNTRUSTED)
                    else -> {
                        when (error?.errorCode) {
                            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                                requireContext().getString(commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
                            WebViewClient.ERROR_AUTHENTICATION -> requireContext().getString(
                                commonR.string.webview_error_AUTHENTICATION,
                            )
                            WebViewClient.ERROR_PROXY_AUTHENTICATION -> requireContext().getString(
                                commonR.string.webview_error_PROXY_AUTHENTICATION,
                            )
                            WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME -> requireContext().getString(
                                commonR.string.webview_error_AUTH_SCHEME,
                            )
                            WebViewClient.ERROR_HOST_LOOKUP -> requireContext().getString(
                                commonR.string.webview_error_HOST_LOOKUP,
                            )
                            else -> message
                        }
                    }
                },
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
        parentFragmentManager.popBackStack()
    }
}
