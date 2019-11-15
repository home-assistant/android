package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase

@Component(dependencies = [DataComponent::class], modules = [DomainModule::class])
interface DomainComponent {

    fun authenticationUseCase(): AuthenticationUseCase

    fun integrationUseCase(): IntegrationUseCase

}