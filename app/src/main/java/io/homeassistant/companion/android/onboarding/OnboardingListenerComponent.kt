package io.homeassistant.companion.android.onboarding

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface OnboardingListenerComponent {

    fun inject(listener: WearOnboardingListener)
}
