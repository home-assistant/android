package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.themes.ThemesRepository
import io.homeassistant.companion.android.common.data.url.UrlRepository

@Component(modules = [DataModule::class])
interface DataComponent {

    fun urlRepository(): UrlRepository

    fun authenticationRepository(): AuthenticationRepository

    fun integrationRepository(): IntegrationRepository

    fun themesRepository(): ThemesRepository
}
