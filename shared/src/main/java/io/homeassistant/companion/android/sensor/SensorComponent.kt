package io.homeassistant.companion.android.sensor

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.SensorScope

@SensorScope
@Component(dependencies = [AppComponent::class])
interface SensorComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent): SensorComponent
    }

    fun inject(worker: SensorWorker)
}
