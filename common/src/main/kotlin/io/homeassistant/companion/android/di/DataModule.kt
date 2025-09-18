package io.homeassistant.companion.android.di

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.authentication.impl.AuthenticationService
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationService
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepository
import io.homeassistant.companion.android.common.data.keychain.KeyChainRepositoryImpl
import io.homeassistant.companion.android.common.data.keychain.KeyStoreRepositoryImpl
import io.homeassistant.companion.android.common.data.keychain.NamedKeyChain
import io.homeassistant.companion.android.common.data.keychain.NamedKeyStore
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.prefs.PrefsRepositoryImpl
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.ServerManagerImpl
import io.homeassistant.companion.android.common.util.di.SuspendProvider
import io.homeassistant.companion.android.common.util.getSharedPreferencesSuspend
import io.homeassistant.companion.android.common.util.tts.AndroidTextToSpeechEngine
import io.homeassistant.companion.android.common.util.tts.TextToSpeechClient
import io.homeassistant.companion.android.di.qualifiers.NamedDeviceId
import io.homeassistant.companion.android.di.qualifiers.NamedInstallId
import io.homeassistant.companion.android.di.qualifiers.NamedIntegrationStorage
import io.homeassistant.companion.android.di.qualifiers.NamedManufacturer
import io.homeassistant.companion.android.di.qualifiers.NamedModel
import io.homeassistant.companion.android.di.qualifiers.NamedOsVersion
import io.homeassistant.companion.android.di.qualifiers.NamedSessionStorage
import io.homeassistant.companion.android.di.qualifiers.NamedThemesStorage
import io.homeassistant.companion.android.di.qualifiers.NamedWearStorage
import java.util.UUID
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
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
        fun providesOkHttpClient(homeAssistantApis: HomeAssistantApis): OkHttpClient = homeAssistantApis.okHttpClient

        @Provides
        @NamedSessionStorage
        @Singleton
        fun provideSessionLocalStorage(@ApplicationContext appContext: Context): LocalStorage = LocalStorageImpl {
            appContext.getSharedPreferencesSuspend("session_0")
        }

        @Provides
        @NamedIntegrationStorage
        @Singleton
        fun provideIntegrationLocalStorage(@ApplicationContext appContext: Context): LocalStorage = LocalStorageImpl {
            appContext.getSharedPreferencesSuspend("integration_0")
        }

        @Provides
        @NamedThemesStorage
        @Singleton
        fun providePrefsLocalStorage(@ApplicationContext appContext: Context): LocalStorage = LocalStorageImpl {
            appContext.getSharedPreferencesSuspend("themes_0")
        }

        @Provides
        @NamedWearStorage
        @Singleton
        fun provideWearPrefsLocalStorage(@ApplicationContext appContext: Context): LocalStorage = LocalStorageImpl {
            appContext.getSharedPreferencesSuspend("wear_0")
        }

        @Provides
        @NamedManufacturer
        @Singleton
        fun provideDeviceManufacturer(): String = Build.MANUFACTURER

        @Provides
        @NamedModel
        @Singleton
        fun provideDeviceModel(): String = Build.MODEL

        @Provides
        @NamedOsVersion
        @Singleton
        fun provideDeviceOsVersion() = Build.VERSION.SDK_INT.toString()

        @SuppressLint("HardwareIds")
        @Provides
        @NamedDeviceId
        @Singleton
        fun provideDeviceId(@ApplicationContext appContext: Context) = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )

        @Provides
        @NamedInstallId
        @Singleton
        fun provideInstallId(@ApplicationContext appContext: Context) = SuspendProvider {
            val storage = provideSessionLocalStorage(appContext)
            storage.getString("install_id") ?: run {
                val uuid = UUID.randomUUID().toString()
                storage.putString("install_id", uuid)
                uuid
            }
        }

        @Provides
        @Singleton
        fun packageManager(@ApplicationContext appContext: Context) = appContext.packageManager

        @Provides
        @Singleton
        fun providesTextToSpeechClient(@ApplicationContext appContext: Context): TextToSpeechClient =
            TextToSpeechClient(appContext, AndroidTextToSpeechEngine(appContext))
    }

    @Binds
    @Singleton
    abstract fun bindPrefsRepository(prefsRepository: PrefsRepositoryImpl): PrefsRepository

    @Binds
    @Singleton
    abstract fun bindWearPrefsRepository(wearPrefsRepository: WearPrefsRepositoryImpl): WearPrefsRepository

    @Binds
    @Singleton
    @NamedKeyChain
    abstract fun bindKeyChainRepository(keyChainRepository: KeyChainRepositoryImpl): KeyChainRepository

    @Binds
    @Singleton
    @NamedKeyStore
    abstract fun bindKeyStore(keyStore: KeyStoreRepositoryImpl): KeyChainRepository

    @Binds
    @Singleton
    abstract fun bindServerManager(serverManager: ServerManagerImpl): ServerManager

    @Multibinds
    abstract fun bindOkHttpClientConfigurator(): Set<@JvmSuppressWildcards OkHttpConfigurator>
}

interface OkHttpConfigurator : (OkHttpClient.Builder) -> Unit
