package io.homeassistant.companion.android.common.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepositoryImpl
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepositoryImpl
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepositoryImpl
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.ServerManagerImpl
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import io.homeassistant.companion.android.common.data.wifi.WifiHelperImpl
import io.homeassistant.companion.android.common.util.tts.AndroidTextToSpeechEngine
import io.homeassistant.companion.android.common.util.tts.TextToSpeechClient
import java.util.UUID
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@Module(
    includes = [
        RepositoryModule::class
    ]
)
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    companion object {
        @Provides
        @Singleton
        fun provideAuthenticationService(homeAssistantApis: HomeAssistantApis): AuthenticationService =
            homeAssistantApis.retrofit.create(AuthenticationService::class.java)

        @Provides
        @Singleton
        fun providesIntegrationService(homeAssistantApis: HomeAssistantApis): IntegrationService =
            homeAssistantApis.retrofit.create(IntegrationService::class.java)

        @Provides
        @Singleton
        fun providesOkHttpClient(homeAssistantApis: HomeAssistantApis): OkHttpClient =
            homeAssistantApis.okHttpClient

        @Provides
        @Named("session")
        @Singleton
        fun provideSessionLocalStorage(@ApplicationContext appContext: Context): LocalStorage =
            LocalStorageImpl(
                appContext.getSharedPreferences(
                    "session_0",
                    Context.MODE_PRIVATE
                )
            )

        @Provides
        @Named("integration")
        @Singleton
        fun provideIntegrationLocalStorage(@ApplicationContext appContext: Context): LocalStorage =
            LocalStorageImpl(
                appContext.getSharedPreferences(
                    "integration_0",
                    Context.MODE_PRIVATE
                )
            )

        @Provides
        @Named("themes")
        @Singleton
        fun providePrefsLocalStorage(@ApplicationContext appContext: Context): LocalStorage =
            LocalStorageImpl(
                appContext.getSharedPreferences(
                    "themes_0",
                    Context.MODE_PRIVATE
                )
            )

        @Provides
        @Named("wear")
        @Singleton
        fun provideWearPrefsLocalStorage(@ApplicationContext appContext: Context): LocalStorage =
            LocalStorageImpl(
                appContext.getSharedPreferences(
                    "wear_0",
                    Context.MODE_PRIVATE
                )
            )

        @Provides
        @Named("manufacturer")
        @Singleton
        fun provideDeviceManufacturer(): String = Build.MANUFACTURER

        @Provides
        @Named("model")
        @Singleton
        fun provideDeviceModel(): String = Build.MODEL

        @Provides
        @Named("osVersion")
        @Singleton
        fun provideDeviceOsVersion() = Build.VERSION.SDK_INT.toString()

        @SuppressLint("HardwareIds")
        @Provides
        @Named("deviceId")
        @Singleton
        fun provideDeviceId(@ApplicationContext appContext: Context) = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        @Provides
        @Named("installId")
        @Singleton
        fun provideInstallId(@ApplicationContext appContext: Context) = runBlocking {
            val storage = provideSessionLocalStorage(appContext)
            storage.getString("install_id") ?: run {
                val uuid = UUID.randomUUID().toString()
                storage.putString("install_id", uuid)
                uuid
            }
        }

        @Provides
        @Singleton
        fun connectivityManager(@ApplicationContext appContext: Context) = appContext.getSystemService<ConnectivityManager>()!!

        @Provides
        @Singleton
        fun wifiManager(@ApplicationContext appContext: Context) = appContext.getSystemService<WifiManager>()

        @Provides
        @Singleton
        fun packageManager(@ApplicationContext appContext: Context) = appContext.packageManager

        @Provides
        @Singleton
        fun providesTextToSpeechClient(
            @ApplicationContext appContext: Context
        ): TextToSpeechClient = TextToSpeechClient(appContext, AndroidTextToSpeechEngine(appContext))
    }

    @Binds
    @Singleton
    abstract fun bindPrefsRepository(prefsRepository: PrefsRepositoryImpl): PrefsRepository

    @Binds
    @Singleton
    abstract fun bindWearPrefsRepository(wearPrefsRepository: WearPrefsRepositoryImpl): WearPrefsRepository

    @Binds
    @Singleton
    abstract fun bindWifiRepository(wifiHelper: WifiHelperImpl): WifiHelper

    @Binds
    @Singleton
    @Named("keyChainRepository")
    abstract fun bindKeyChainRepository(keyChainRepository: KeyChainRepositoryImpl): KeyChainRepository

    @Binds
    @Singleton
    @Named("keyStore")
    abstract fun bindKeyStore(keyStore: KeyStoreRepositoryImpl): KeyChainRepository

    @Binds
    @Singleton
    abstract fun bindServerManager(serverManager: ServerManagerImpl): ServerManager
}
