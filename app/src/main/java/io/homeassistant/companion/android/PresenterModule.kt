package io.homeassistant.companion.android

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.launch.LaunchPresenter
import io.homeassistant.companion.android.launch.LaunchPresenterImpl
import io.homeassistant.companion.android.launch.LaunchView
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenter
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenterImpl
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationView
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenter
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenterImpl
import io.homeassistant.companion.android.onboarding.manual.ManualSetupView
import io.homeassistant.companion.android.webview.WebView
import io.homeassistant.companion.android.webview.WebViewPresenter
import io.homeassistant.companion.android.webview.WebViewPresenterImpl

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var authenticationView: AuthenticationView
    private lateinit var manualSetupView: ManualSetupView
    private lateinit var webView: WebView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(authenticationView: AuthenticationView) {
        this.authenticationView = authenticationView
    }

    constructor(manualSetupView: ManualSetupView) {
        this.manualSetupView = manualSetupView
    }

    constructor(webView: WebView) {
        this.webView = webView
    }

    @Provides
    fun provideLaunchView() = launchView

    @Provides
    fun provideAuthenticationView() = authenticationView

    @Provides
    fun provideManualSetupView() = manualSetupView

    @Provides
    fun provideWebView() = webView

    @Module
    interface Declaration {

        @Binds
        fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter

        @Binds
        fun bindAuthenticationPresenterImpl(presenter: AuthenticationPresenterImpl): AuthenticationPresenter

        @Binds
        fun bindManualSetupPresenter(presenter: ManualSetupPresenterImpl): ManualSetupPresenter

        @Binds
        fun bindWebViewPresenterImpl(presenter: WebViewPresenterImpl): WebViewPresenter


    }
}
