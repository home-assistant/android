package io.homeassistant.companion.android.common.dagger

import android.app.Application
import android.content.Context
import dagger.BindsInstance
import dagger.Component
import io.homeassistant.companion.android.common.actions.WearActionRepository
import io.homeassistant.companion.android.common.database.DatabaseModule
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository
import io.homeassistant.companion.android.domain.integration.IntegrationRepository
import io.homeassistant.companion.android.domain.url.UrlRepository
import io.homeassistant.companion.android.domain.widgets.WidgetRepository

@DataScope
@Component(modules = [DataModule::class, DatabaseModule::class])
interface DataComponent {

    @Component.Factory
    interface Factory {
        fun create(
            dataModule: DataModule,
            @BindsInstance application: Application
        ): DataComponent
    }

    fun context(): Context

    fun urlRepository(): UrlRepository

    fun authenticationRepository(): AuthenticationRepository

    fun integrationRepository(): IntegrationRepository

    fun widgetRepository(): WidgetRepository

    fun wearActionsRepository(): WearActionRepository
}
