package io.homeassistant.companion.android

import dagger.Binds
import dagger.Module
import dagger.Provides
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

@Module(includes = [PresenterModule.Declaration::class])
class PresenterModule {

    private lateinit var launchView: LaunchView
    private lateinit var onBoardingView: OnboardingView
    private lateinit var authenticationView: AuthenticationView

    constructor(launchView: LaunchView) {
        this.launchView = launchView
    }

    constructor(onBoardingView: OnboardingView) {
        this.onBoardingView = onBoardingView
    }

    constructor(authenticationView: AuthenticationView) {
        this.authenticationView = authenticationView
    }

    @Provides
    fun provideLaunchView() = launchView

    @Provides
    fun provideOnboardingView() = onBoardingView

    @Provides
    fun provideAuthenticationView() = authenticationView

    @Module
    interface Declaration {

        @Binds
        fun bindLaunchPresenter(presenter: LaunchPresenterImpl): LaunchPresenter

        @Binds
        fun bindOnboardingPresenter(presenter: OnboardingPresenterImpl): OnboardingPresenter

        @Binds
        fun bindAuthenticationPresenter(presenter: AuthenticationPresenterImpl): AuthenticationPresenter
    }
}
