package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository

@Component(modules = [DataModule::class])
interface AppComponent {

    fun urlUseCase(): UrlRepository

    fun authenticationUseCase(): AuthenticationRepository

    fun integrationUseCase(): IntegrationRepository

    fun prefsUseCase(): PrefsRepository
}
