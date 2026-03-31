package io.homeassistant.companion.android.onboarding.integration

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface MobileAppIntegrationModule {

    @Binds
    fun mobileAppIntegrationPresenter(
        mobileAppIntegrationPresenterImpl: MobileAppIntegrationPresenterImpl,
    ): MobileAppIntegrationPresenter
}
