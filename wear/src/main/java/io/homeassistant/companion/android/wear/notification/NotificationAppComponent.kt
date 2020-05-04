package io.homeassistant.companion.android.wear.notification

import android.content.Context
import dagger.BindsInstance
import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.NotificationScope
import io.homeassistant.companion.android.wear.background.BackgroundModule

@NotificationScope
@Component(
    dependencies = [AppComponent::class, DomainComponent::class],
    modules = [BackgroundModule::class]
)
interface NotificationAppComponent {

    @Component.Factory
    interface Factory {
        fun create(
            appComponent: AppComponent,
            domainComponent: DomainComponent,
            backgroundModule: BackgroundModule,
            @BindsInstance context: Context
        ): NotificationAppComponent
    }

    fun inject(receiver: NotificationActionReceiver)
}