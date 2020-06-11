package io.homeassistant.companion.android.notification

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.NotificationScope

@NotificationScope
@Component(dependencies = [AppComponent::class])
interface NotificationComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent): NotificationComponent
    }

    fun inject(service: AbstractMessagingService)
    fun inject(receiver: AbstractNotificationActionReceiver)
}
