package io.homeassistant.companion.android.common.dagger

import android.content.Context
import dagger.Component
import io.homeassistant.companion.android.common.actions.WearActionUseCase
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.domain.widgets.WidgetUseCase

@DomainScope
@Component(dependencies = [DataComponent::class], modules = [DomainModule::class])
interface DomainComponent {

    @Component.Factory
    interface Factory {
        fun create(dataComponent: DataComponent): DomainComponent
    }

    fun context(): Context

    fun urlUseCase(): UrlUseCase

    fun authenticationUseCase(): AuthenticationUseCase

    fun integrationUseCase(): IntegrationUseCase

    fun widgetUseCase(): WidgetUseCase

    fun wearActionsUseCase(): WearActionUseCase

}
