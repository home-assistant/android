package io.homeassistant.companion.android.onboarding

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface OnboardingModule {

    @Binds
    fun onboardingPresenter(onboardingPresenterImpl: OnboardingPresenterImpl): OnboardingPresenter
}
