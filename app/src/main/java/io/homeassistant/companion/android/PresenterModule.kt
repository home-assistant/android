package io.homeassistant.companion.android

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.launch.LaunchPresenter
import io.homeassistant.companion.android.launch.LaunchPresenterImpl
import io.homeassistant.companion.android.launch.LaunchView
import io.homeassistant.companion.android.lock.LockPresenter
import io.homeassistant.companion.android.lock.LockPresenterImpl
import io.homeassistant.companion.android.lock.LockView
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenter
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenterImpl
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationView
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryPresenter
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryPresenterImpl
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryView
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenterImpl
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationView
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenter
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenterImpl
import io.homeassistant.companion.android.onboarding.manual.ManualSetupView
import io.homeassistant.companion.android.settings.SettingsPresenter
import io.homeassistant.companion.android.settings.SettingsPresenterImpl
import io.homeassistant.companion.android.settings.SettingsView
import io.homeassistant.companion.android.webview.WebView
import io.homeassistant.companion.android.webview.WebViewPresenter
import io.homeassistant.companion.android.webview.WebViewPresenterImpl

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var discoveryView: DiscoveryView
    private lateinit var authenticationView: AuthenticationView
    private lateinit var manualSetupView: ManualSetupView
    private lateinit var mobileAppIntegrationView: MobileAppIntegrationView
    private lateinit var settingsView: SettingsView
    private lateinit var webView: WebView
    private lateinit var lockView: LockView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(discoveryView: DiscoveryView) {
        this.discoveryView = discoveryView
    }

    constructor(authenticationView: AuthenticationView) {
        this.authenticationView = authenticationView
    }

    constructor(manualSetupView: ManualSetupView) {
        this.manualSetupView = manualSetupView
    }

    constructor(mobileAppIntegrationView: MobileAppIntegrationView) {
        this.mobileAppIntegrationView = mobileAppIntegrationView
    }

    constructor(settingsView: SettingsView) {
        this.settingsView = settingsView
    }

    constructor(webView: WebView) {
        this.webView = webView
    }

    constructor(lockView: LockView) {
        this.lockView = lockView
    }

    @Provides
    fun provideLaunchView() = launchView

    @Provides
    fun providesDiscoveryView() = discoveryView

    @Provides
    fun provideAuthenticationView() = authenticationView

    @Provides
    fun provideManualSetupView() = manualSetupView

    @Provides
    fun provideMobileAppIntegrationView() = mobileAppIntegrationView

    @Provides
    fun provideSettingsView() = settingsView

    @Provides
    fun provideWebView() = webView

    @Provides
    fun provideLockView() = lockView

    @Module
    interface Declaration {

        @Binds
        fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter

        @Binds
        fun bindDiscoveryPresenter(presenter: DiscoveryPresenterImpl): DiscoveryPresenter

        @Binds
        fun bindAuthenticationPresenterImpl(presenter: AuthenticationPresenterImpl): AuthenticationPresenter

        @Binds
        fun bindManualSetupPresenter(presenter: ManualSetupPresenterImpl): ManualSetupPresenter

        @Binds
        fun bindMobileAppPresenter(presenter: MobileAppIntegrationPresenterImpl): MobileAppIntegrationPresenter

        @Binds
        fun bindSettingsPresenter(presenter: SettingsPresenterImpl): SettingsPresenter

        @Binds
        fun bindWebViewPresenterImpl(presenter: WebViewPresenterImpl): WebViewPresenter

        @Binds
        fun bindLockPresenter(presenter: LockPresenterImpl): LockPresenter
    }
}
