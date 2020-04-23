package io.homeassistant.companion.android.widgets

import dagger.Component
import io.homeassistant.companion.android.common.dagger.AppComponent
import io.homeassistant.companion.android.common.dagger.DomainComponent
import io.homeassistant.companion.android.common.dagger.ProviderScope

@ProviderScope
@Component(dependencies = [AppComponent::class, DomainComponent::class])
interface ProviderComponent {

    @Component.Factory
    interface Factory {
        fun create(appComponent: AppComponent, domainComponent: DomainComponent): ProviderComponent
    }

    fun inject(receiver: ButtonWidget)

    fun inject(activity: ButtonWidgetConfigureActivity)
}
