package io.homeassistant.companion.android.background

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.ServiceScope

@ServiceScope
@Component(dependencies = [AppComponent::class])
interface ServiceComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent): ServiceComponent
    }

    fun inject(service: WearDataListenerService)

}
