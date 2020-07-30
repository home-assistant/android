package io.homeassistant.companion.android.notifications

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent

@Component(dependencies = [AppComponent::class])
interface ServiceComponent {

    fun inject(service: MessagingService)

    fun inject(receiver: NotificationActionReceiver)
}
