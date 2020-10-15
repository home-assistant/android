package io.homeassistant.companion.android.notifications

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.settings.notification.NotificationDetailFragment
import io.homeassistant.companion.android.settings.notification.NotificationHistoryFragment

@Component(dependencies = [AppComponent::class])
interface ServiceComponent {

    fun inject(service: MessagingService)

    fun inject(receiver: NotificationActionReceiver)

    fun inject(receiver: NotificationDeleteReceiver)

    fun inject(notificationHistoryFragment: NotificationHistoryFragment)

    fun inject(notificationDetailFragment: NotificationDetailFragment)
}
