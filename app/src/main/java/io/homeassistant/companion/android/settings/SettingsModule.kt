package io.homeassistant.companion.android.settings

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenter
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationPresenterImpl
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryPresenter
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryPresenterImpl
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenter
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationPresenterImpl
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenter
import io.homeassistant.companion.android.onboarding.manual.ManualSetupPresenterImpl

@Module
@InstallIn(ActivityComponent::class)
abstract class SettingsModule {

    companion object {
        @Provides
        fun settingsView(@ActivityContext context: Context): SettingsView = context as SettingsView
    }

    @Binds
    abstract fun settingsPresenter(settingsPresenterImpl: SettingsPresenterImpl): SettingsPresenter

}