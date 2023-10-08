package io.homeassistant.companion.android.onboarding.authentication

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.onboarding.OnboardingViewModel
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationFragment
import io.homeassistant.companion.android.themes.ThemesManager
import io.homeassistant.companion.android.util.TLSWebViewClient
import io.homeassistant.companion.android.util.isStarted
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Named
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class AuthenticationFragment : Fragment() {

    companion object {
        private const val TAG = "AuthenticationFragment"
        private const val AUTH_CALLBACK = "homeassistant://auth-callback"
    }

    private val viewModel by activityViewModels<OnboardingViewModel>()

    private var authUrl: String? = null

    @Inject
    lateinit var themesManager: ThemesManager

    @Inject
    @Named("keyChainRepository")
    lateinit var keyChainRepository: KeyChainRepository

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MdcTheme {
                    AndroidView({
                        WebView(requireContext()).apply {
                            themesManager.setThemeForWebView(requireContext(), settings)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = settings.userAgentString + " ${HomeAssistantApis.USER_AGENT_STRING}"
                            webViewClient = object : TLSWebViewClient(keyChainRepository) {
                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                                    return onRedirect(url)
                                }

                                @RequiresApi(Build.VERSION_CODES.M)
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.url?.toString() == authUrl) {
                                        Log.e(
                                            TAG,
                                            "onReceivedError: Status Code: ${error?.errorCode} Description: ${error?.description}"
                                        )
                                        showError(
                                            requireContext().getString(
                                                commonR.string.error_http_generic,
                                                error?.errorCode,
                                                if (error?.description.isNullOrBlank()) {
                                                    commonR.string.no_description
                                                } else {
                                                    error?.description
                                                }
                                            ),
                                            null,
                                            error
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    if (request?.url?.toString() == authUrl) {
                                        Log.e(
                                            TAG,
                                            "onReceivedHttpError: Status Code: ${errorResponse?.statusCode} Description: ${errorResponse?.reasonPhrase}"
                                        )
                                        if (isTLSClientAuthNeeded && !isCertificateChainValid) {
                                            showError(
                                                requireContext().getString(commonR.string.tls_cert_expired_message),
                                                null,
                                                null
                                            )
                                        } else if (isTLSClientAuthNeeded && errorResponse?.statusCode == 400) {
                                            showError(
                                                requireContext().getString(commonR.string.tls_cert_not_found_message),
                                                null,
                                                null
                                            )
                                        } else {
                                            showError(
                                                requireContext().getString(
                                                    commonR.string.error_http_generic,
                                                    errorResponse?.statusCode,
                                                    if (errorResponse?.reasonPhrase.isNullOrBlank()) {
                                                        requireContext().getString(commonR.string.no_description)
                                                    } else {
                                                        errorResponse?.reasonPhrase
                                                    }
                                                ),
                                                null,
                                                null
                                            )
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    super.onReceivedSslError(view, handler, error)
                                    Log.e(TAG, "onReceivedSslError: $error")
                                    showError(requireContext().getString(commonR.string.error_ssl), error, null)
                                }
                            }
                            authUrl = buildAuthUrl(viewModel.manualUrl.value)
                            loadUrl(authUrl!!)
                        }
                    })
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
            Log.e(TAG, "Unable to build authentication URL", e)
            Toast.makeText(context, commonR.string.error_connection_failed, Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
            ""
        }
    }

    private fun onRedirect(url: String): Boolean {
        val code = Uri.parse(url).getQueryParameter("code")
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
                    SslError.SSL_DATE_INVALID -> requireContext().getString(commonR.string.webview_error_SSL_DATE_INVALID)
                    SslError.SSL_EXPIRED -> requireContext().getString(commonR.string.webview_error_SSL_EXPIRED)
                    SslError.SSL_IDMISMATCH -> requireContext().getString(commonR.string.webview_error_SSL_IDMISMATCH)
                    SslError.SSL_INVALID -> requireContext().getString(commonR.string.webview_error_SSL_INVALID)
                    SslError.SSL_NOTYETVALID -> requireContext().getString(commonR.string.webview_error_SSL_NOTYETVALID)
                    SslError.SSL_UNTRUSTED -> requireContext().getString(commonR.string.webview_error_SSL_UNTRUSTED)
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            when (error?.errorCode) {
                                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                                    requireContext().getString(commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
                                WebViewClient.ERROR_AUTHENTICATION -> requireContext().getString(commonR.string.webview_error_AUTHENTICATION)
                                WebViewClient.ERROR_PROXY_AUTHENTICATION -> requireContext().getString(commonR.string.webview_error_PROXY_AUTHENTICATION)
                                WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME -> requireContext().getString(commonR.string.webview_error_AUTH_SCHEME)
                                WebViewClient.ERROR_HOST_LOOKUP -> requireContext().getString(commonR.string.webview_error_HOST_LOOKUP)
                                else -> message
                            }
                        } else {
                            message
                        }
                    }
                }
            )
            .setPositiveButton(android.R.string.ok) { _, _ -> }
            .show()
        parentFragmentManager.popBackStack()
    }
}
