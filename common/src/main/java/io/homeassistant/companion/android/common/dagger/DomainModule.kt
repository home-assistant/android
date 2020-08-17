package io.homeassistant.companion.android.common.dagger

import dagger.Binds
import dagger.Module
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCaseImpl
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCaseImpl
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCaseImpl

@Module
interface DomainModule {

    @Binds
    fun bindUrlUseCase(useCaseImpl: UrlUseCaseImpl): UrlUseCase

    @Binds
    fun bindAuthenticationUseCase(useCaseImpl: AuthenticationUseCaseImpl): AuthenticationUseCase

    @Binds
    fun bindIntegrationUseCase(useCaseImpl: IntegrationUseCaseImpl): IntegrationUseCase
}
