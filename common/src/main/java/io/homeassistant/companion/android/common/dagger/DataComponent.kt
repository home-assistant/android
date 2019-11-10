package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository

@Component(modules = [DataModule::class])
interface DataComponent {

    fun authenticationRepository(): AuthenticationRepository

}