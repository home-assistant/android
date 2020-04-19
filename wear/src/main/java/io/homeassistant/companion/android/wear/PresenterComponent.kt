package io.homeassistant.companion.android.wear

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.wear.launch.LaunchActivity

@Component(dependencies = [AppComponent::class], modules = [PresenterModule::class])
interface PresenterComponent {

    fun inject(activity: LaunchActivity)
}
