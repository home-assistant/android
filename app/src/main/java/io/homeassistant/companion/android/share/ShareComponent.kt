package io.homeassistant.companion.android.share

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface ShareComponent {

    fun inject(activity: ShareActivity)
}
