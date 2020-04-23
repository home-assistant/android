package io.homeassistant.companion.android.notifications

import dagger.Component
import io.homeassistant.companion.android.background.WearDataListenerService
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.ServiceScope

@ServiceScope
@Component(dependencies = [AppComponent::class, DomainComponent::class])
interface ServiceComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent, domainComponent: DomainComponent): ServiceComponent
    }

    fun inject(service: MessagingService)

    fun inject(receiver: NotificationActionReceiver)

    fun inject(service: WearDataListenerService)

}
