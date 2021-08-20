package io.homeassistant.companion.android

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import io.homeassistant.companion.android.onboarding.manual_setup.ManualSetupActivity

@Component(dependencies = [AppComponent::class], modules = [PresenterModule::class])
interface PresenterComponent {

    fun inject(activity: OnboardingActivity)

    fun inject(activity: AuthenticationActivity)

    fun inject(activity: MobileAppIntegrationActivity)

    fun inject(activity: ManualSetupActivity)

    fun inject(activity: HomeActivity)
}
