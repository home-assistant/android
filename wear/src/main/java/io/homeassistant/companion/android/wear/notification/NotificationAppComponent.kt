package io.homeassistant.companion.android.wear.notification

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.NotificationScope
import io.homeassistant.companion.android.wear.background.BackgroundModule

@NotificationScope
@Component(
    dependencies = [AppComponent::class],
    modules = [BackgroundModule::class]
)
interface NotificationAppComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent): NotificationAppComponent
    }

    fun inject(receiver: NotificationActionReceiver)
}
