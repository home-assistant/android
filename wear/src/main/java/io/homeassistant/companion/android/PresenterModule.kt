package io.homeassistant.companion.android

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.home.HomePresenter
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.home.HomeView
import io.homeassistant.companion.android.launch.LaunchPresenter
import io.homeassistant.companion.android.launch.LaunchPresenterImpl
import io.homeassistant.companion.android.launch.LaunchView
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.OnboardingPresenter
import io.homeassistant.companion.android.onboarding.OnboardingPresenterImpl
import io.homeassistant.companion.android.onboarding.OnboardingView
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenter
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenterImpl
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationView
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenterImpl
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationView

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var onBoardingView: OnboardingView
    private lateinit var authenticationView: AuthenticationView
    private lateinit var mobileAppIntegrationView: MobileAppIntegrationView
    private lateinit var homeView: HomeView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(onBoardingView: OnboardingView) {
        this.onBoardingView = onBoardingView
    }

    constructor(authenticationView: AuthenticationView) {
        this.authenticationView = authenticationView
    }

    constructor(mobileAppIntegrationView: MobileAppIntegrationView) {
        this.mobileAppIntegrationView = mobileAppIntegrationView
    }

    constructor(homeView: HomeView) {
        this.homeView = homeView
    }

    @Provides
    fun provideLaunchView() = launchView

    @Provides
    fun provideOnboardingView() = onBoardingView

    @Provides
    fun provideAuthenticationView() = authenticationView

    @Provides
    fun provideMobileAppIntegrationView() = mobileAppIntegrationView

    @Provides
    fun provideHomeView() = homeView

    @Module
    interface Declaration {

        @Binds
        fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter

        @Binds
        fun bindOnboardingPresenter(presenter: OnboardingPresenterImpl): OnboardingPresenter

        @Binds
        fun bindAuthenticationPresenter(presenter: AuthenticationPresenterImpl): AuthenticationPresenter

        @Binds
        fun bindMobileAppIntegrationPresenter(presenter: MobileAppIntegrationPresenterImpl): MobileAppIntegrationPresenter

        @Binds
        fun bindHomePresenter(presenter: HomePresenterImpl): HomePresenter
    }
}
