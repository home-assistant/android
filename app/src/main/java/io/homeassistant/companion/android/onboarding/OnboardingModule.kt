package io.homeassistant.companion.android.onboarding

import android.app.Activity
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationListener
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenterImpl

@Module
@InstallIn(ActivityComponent::class)
abstract class OnboardingModule {

    companion object {

        @Provides
        fun mobileAppIntegrationListener(activity: Activity): MobileAppIntegrationListener =
            activity as MobileAppIntegrationListener
    }

    @Binds
    abstract fun mobileAppIntegrationPresenter(mobileAppIntegrationPresenterImpl: MobileAppIntegrationPresenterImpl): MobileAppIntegrationPresenter
}
