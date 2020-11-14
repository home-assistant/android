package io.homeassistant.companion.android.settings

import dagger.Component
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface SettingsComponent {

    fun inject(activity: BaseActivity)
}
