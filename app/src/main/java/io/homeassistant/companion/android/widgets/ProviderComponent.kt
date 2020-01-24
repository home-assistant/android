package io.homeassistant.companion.android.widgets

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface ProviderComponent {

    fun inject(receiver: ButtonWidget)

    fun inject(activity: ButtonWidgetConfigureActivity)
}
