package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase

@Component(dependencies = [DomainComponent::class])
interface AppComponent {

    fun authenticationUseCase(): AuthenticationUseCase

    fun integrationUseCase(): IntegrationUseCase
}
