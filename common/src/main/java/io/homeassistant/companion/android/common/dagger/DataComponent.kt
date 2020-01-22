package io.homeassistant.companion.android.common.dagger

import dagger.Component
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import io.homeassistant.companion.android.domain.url.UrlRepository
import io.homeassistant.companion.android.domain.widgets.WidgetRepository

@Component(modules = [DataModule::class])
interface DataComponent {

    fun urlRepository(): UrlRepository

    fun authenticationRepository(): AuthenticationRepository

    fun integrationRepository(): IntegrationRepository

    fun widgetRepository(): WidgetRepository
}
