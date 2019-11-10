package io.homeassistant.companion.android.common.dagger

import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.data.HomeAssistantRetrofit
import io.homeassistant.companion.android.data.LocalStorage
import io.homeassistant.companion.android.data.authentication.AuthenticationRepositoryImpl
import io.homeassistant.companion.android.data.authentication.AuthenticationService
import io.homeassistant.companion.android.domain.authentication.AuthenticationRepository

@Module(includes = [DataModule.Declaration::class])
class DataModule(
    private val url: String,
    private val localStorage: LocalStorage
) {

    @Provides
    fun provideEndPoint() = url

    @Provides
    fun provideAuthenticationService(homeAssistantRetrofit: HomeAssistantRetrofit) = homeAssistantRetrofit.retrofit.create(AuthenticationService::class.java)

    @Provides
    fun provideLocalStorage() = localStorage


    @Module
    interface Declaration {

        @Binds
        fun bindAuthenticationRepositoryImpl(repository: AuthenticationRepositoryImpl): AuthenticationRepository

    }

}
