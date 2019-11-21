package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.IntegrationRepository

@Component(modules = [DataModule::class])
interface DataComponent {

    fun authenticationRepository(): AuthenticationRepository

    fun integrationRepository(): IntegrationRepository
}
