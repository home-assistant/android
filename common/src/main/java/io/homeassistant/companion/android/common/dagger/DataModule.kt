package io.homeassistant.companion.android.common.dagger

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.HomeAssistantRetrofit
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import io.homeassistant.companion.android.common.data.wifi.WifiHelperImpl
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(ApplicationComponent::class)
object DataModule {

    private const val instanceId = 0

    @Provides
    @Singleton
    fun provideAuthenticationService(homeAssistantRetrofit: HomeAssistantRetrofit): AuthenticationService =
        homeAssistantRetrofit.retrofit.create(AuthenticationService::class.java)

    @Provides
    @Singleton
    fun providesIntegrationService(homeAssistantRetrofit: HomeAssistantRetrofit): IntegrationService =
        homeAssistantRetrofit.retrofit.create(IntegrationService::class.java)

    @Provides
    fun providesWifiHelper(application: Application): WifiHelper {
        return WifiHelperImpl(application.getSystemService(Context.WIFI_SERVICE) as WifiManager)
    }

    @Provides
    @Named("url")
    fun provideUrlLocalStorage(application: Application): LocalStorage {
        return LocalStorageImpl(
            application.getSharedPreferences(
                "url_$instanceId",
                Context.MODE_PRIVATE
            )
        )
    }

    @Provides
    @Named("session")
    fun provideSessionLocalStorage(application: Application): LocalStorage {
        return LocalStorageImpl(
            application.getSharedPreferences(
                "session_$instanceId",
                Context.MODE_PRIVATE
            )
        )
    }

    @Provides
    @Named("integration")
    fun provideIntegrationLocalStorage(application: Application): LocalStorage {
        return LocalStorageImpl(
            application.getSharedPreferences(
                "integration_$instanceId",
                Context.MODE_PRIVATE
            )
        )
    }

    @Provides
    @Named("themes")
    fun provideThemesLocalStorage(application: Application): LocalStorage {
        return LocalStorageImpl(
            application.getSharedPreferences(
                "themes",
                Context.MODE_PRIVATE
            )
        )
    }

    @Provides
    @Named("manufacturer")
    fun provideDeviceManufacturer(): String = Build.MANUFACTURER

    @Provides
    @Named("model")
    fun provideDeviceModel(): String = Build.MODEL

    @Provides
    @Named("osVersion")
    fun provideDeviceOsVersion() = Build.VERSION.SDK_INT.toString()

    @SuppressLint("HardwareIds")
    @Provides
    @Named("deviceId")
    fun provideDeviceId(application: Application): String {
        return Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
    }
}
