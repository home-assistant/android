package io.homeassistant.companion.android

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.authentication.AuthenticationActivity

@Component(dependencies = [AppComponent::class], modules = [PresenterModule::class])
interface PresenterComponent {

    fun inject(activity: LaunchActivity)

    fun inject(activity: OnboardingActivity)

    fun inject(activity: AuthenticationActivity)
}
