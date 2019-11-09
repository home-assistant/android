package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase

@Component(dependencies = [DomainComponent::class])
interface AppComponent {

    fun authenticationUseCase(): AuthenticationUseCase

}