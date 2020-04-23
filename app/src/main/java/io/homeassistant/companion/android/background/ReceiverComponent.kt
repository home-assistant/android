package io.homeassistant.companion.android.background

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.ReceiverScope

@ReceiverScope
@Component(dependencies = [AppComponent::class, DomainComponent::class])
interface ReceiverComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent, domainComponent: DomainComponent): ReceiverComponent
    }

    fun inject(receiver: LocationBroadcastReceiver)
}
