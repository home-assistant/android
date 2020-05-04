package io.homeassistant.companion.android.notification

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.NotificationScope

@NotificationScope
@Component(dependencies = [AppComponent::class, DomainComponent::class])
interface NotificationComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent, domainComponent: DomainComponent): NotificationComponent
    }

    fun inject(service: AbstractMessagingService)
    fun inject(receiver: AbstractNotificationActionReceiver)
}
