package io.homeassistant.companion.android.common.dagger

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.homeassistant.companion.android.common.data.HomeAssistantRetrofit
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationRepositoryImpl
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepositoryImpl
import io.homeassistant.companion.android.common.data.url.UrlRepository
import io.homeassistant.companion.android.common.data.url.UrlRepositoryImpl
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import javax.inject.Named

@Module(includes = [DataModule.Declaration::class])
class DataModule(
    private val urlStorage: LocalStorage,
    private val sessionLocalStorage: LocalStorage,
    private val integrationLocalStorage: LocalStorage,
    private val prefsLocalStorage: LocalStorage,
    private val wifiHelper: WifiHelper,
    private val deviceId: String
) {

    @Provides
    fun provideAuthenticationService(homeAssistantRetrofit: HomeAssistantRetrofit): AuthenticationService =
        homeAssistantRetrofit.retrofit.create(AuthenticationService::class.java)

    @Provides
    fun providesIntegrationService(homeAssistantRetrofit: HomeAssistantRetrofit): IntegrationService =
        homeAssistantRetrofit.retrofit.create(IntegrationService::class.java)

    @Provides
    fun providesWifiHelper() = wifiHelper

    @Provides
    @Named("url")
    fun provideUrlLocalStorage() = urlStorage

    @Provides
    @Named("session")
    fun provideSessionLocalStorage() = sessionLocalStorage

    @Provides
    @Named("integration")
    fun provideIntegrationLocalStorage() = integrationLocalStorage

    @Provides
    @Named("themes")
    fun providePrefsLocalStorage() = prefsLocalStorage

    @Provides
    @Named("manufacturer")
    fun provideDeviceManufacturer(): String = Build.MANUFACTURER

    @Provides
    @Named("model")
    fun provideDeviceModel(): String = Build.MODEL

    @Provides
    @Named("osVersion")
    fun provideDeviceOsVersion() = Build.VERSION.SDK_INT.toString()

    @Provides
    @Named("deviceId")
    fun provideDeviceId() = deviceId

    @Module
    interface Declaration {
        @Binds
        fun bindUrlRepositoryImpl(repository: UrlRepositoryImpl): UrlRepository

        @Binds
        fun bindAuthenticationRepositoryImpl(repository: AuthenticationRepositoryImpl): AuthenticationRepository

        @Binds
        fun bindIntegrationService(repository: IntegrationRepositoryImpl): IntegrationRepository

        @Binds
        fun bindPrefsRepositoryImpl(repository: PrefsRepositoryImpl): PrefsRepository
    }
}
