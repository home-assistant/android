package io.homeassistant.companion.android.common.dagger

import dagger.Binds
import dagger.Module
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCaseImpl

@Module
interface DomainModule {

    @Binds
    fun bindAuthenticationUseCase(useCaseImpl: AuthenticationUseCaseImpl) : AuthenticationUseCase

}
