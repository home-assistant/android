package io.homeassistant.companion.android.sensors

import dagger.Component
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.SensorScope

@SensorScope
@Component(dependencies = [AppComponent::class, DomainComponent::class])
interface SensorComponent {

    @Component.Factory
    interface Factory {
        fun create(
            appComponent: AppComponent,
            domainComponent: DomainComponent
        ): SensorComponent
    }

    fun inject(worker: SensorWorker)
}
