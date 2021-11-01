package io.homeassistant.companion.android.viewModels

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface ViewModuleComponent {

    fun inject(entityViewModel: EntityViewModel)
}
