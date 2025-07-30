package io.homeassistant.companion.android.onboarding.connection

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.onboarding.connection.navigation.ConnectionRoute
import io.homeassistant.companion.android.util.TLSWebViewClient
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

internal sealed interface ConnectionNavigationEvent {
    data class Error(@StringRes val message: Int) : ConnectionNavigationEvent
    data object Authenticated : ConnectionNavigationEvent
}

private const val AUTH_CALLBACK = "homeassistant://auth-callback"

@HiltViewModel
internal class ConnectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @Named("keyChainRepository")
    val keyChainRepository: KeyChainRepository,
) : ViewModel() {
    private val route: ConnectionRoute = savedStateHandle.toRoute()
    private val _navigationEventsFlow = MutableSharedFlow<ConnectionNavigationEvent>()
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    // TODO we could wrap the 2 flows into a State object and have only one flow
    private val _urlFlow = MutableStateFlow<String?>(null)
    val urlFlow = _urlFlow.asStateFlow()

    private val _isLoadingFlow = MutableStateFlow(true)
    val isLoadingFlow = _isLoadingFlow.asStateFlow()

    val webViewClient: WebViewClient = object : TLSWebViewClient(keyChainRepository) {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            _isLoadingFlow.update { false }
        }
    }

    init {
        viewModelScope.launch {
            buildAuthUrl(route.url)
        }
    }

    private suspend fun buildAuthUrl(base: String) {
        try {
            val url = base.toHttpUrl()
            val builder = if (url.host.endsWith("ui.nabu.casa", true)) {
                HttpUrl.Builder()
                    .scheme(url.scheme)
                    .host(url.host)
                    .port(url.port)
            } else {
                url.newBuilder()
            }
            _urlFlow.emit(
                builder
                    .addPathSegments("auth/authorize")
                    .addEncodedQueryParameter("response_type", "code")
                    .addEncodedQueryParameter("client_id", AuthenticationService.CLIENT_ID)
                    .addEncodedQueryParameter("redirect_uri", AUTH_CALLBACK)
                    .build()
                    .toString(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Unable to build authentication URL")
            _navigationEventsFlow.emit(ConnectionNavigationEvent.Error(R.string.error_connection_failed))
        }
    }
}
