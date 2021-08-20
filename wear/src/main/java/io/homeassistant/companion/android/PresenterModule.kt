package io.homeassistant.companion.android

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.home.HomePresenter
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.home.HomeView
import io.homeassistant.companion.android.onboarding.OnboardingPresenter
import io.homeassistant.companion.android.onboarding.OnboardingPresenterImpl
import io.homeassistant.companion.android.onboarding.OnboardingView
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenter
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenterImpl
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationView
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenterImpl
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationView
import io.homeassistant.companion.android.onboarding.manual_setup.ManualSetupPresenter
import io.homeassistant.companion.android.onboarding.manual_setup.ManualSetupPresenterImpl
import io.homeassistant.companion.android.onboarding.manual_setup.ManualSetupView

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var onBoardingView: OnboardingView
    private lateinit var authenticationView: AuthenticationView
    private lateinit var mobileAppIntegrationView: MobileAppIntegrationView
    private lateinit var manualSetupView: ManualSetupView
    private lateinit var homeView: HomeView

    constructor(onBoardingView: OnboardingView) {
        this.onBoardingView = onBoardingView
    }

    constructor(authenticationView: AuthenticationView) {
        this.authenticationView = authenticationView
    }

    constructor(mobileAppIntegrationView: MobileAppIntegrationView) {
        this.mobileAppIntegrationView = mobileAppIntegrationView
    }

    constructor(manualSetupView: ManualSetupView) {
        this.manualSetupView = manualSetupView
    }

    constructor(homeView: HomeView) {
        this.homeView = homeView
    }

    @Provides
    fun provideOnboardingView() = onBoardingView

    @Provides
    fun provideAuthenticationView() = authenticationView

    @Provides
    fun provideMobileAppIntegrationView() = mobileAppIntegrationView

    @Provides
    fun provideManualSetupView() = manualSetupView

    @Provides
    fun provideHomeView() = homeView

    @Module
    interface Declaration {

        @Binds
        fun bindOnboardingPresenter(presenter: OnboardingPresenterImpl): OnboardingPresenter

        @Binds
        fun bindAuthenticationPresenter(presenter: AuthenticationPresenterImpl): AuthenticationPresenter

        @Binds
        fun bindMobileAppIntegrationPresenter(presenter: MobileAppIntegrationPresenterImpl): MobileAppIntegrationPresenter

        @Binds
        fun bindManualSetupPresenter(presenter: ManualSetupPresenterImpl): ManualSetupPresenter

        @Binds
        fun bindHomePresenter(presenter: HomePresenterImpl): HomePresenter
    }
}
