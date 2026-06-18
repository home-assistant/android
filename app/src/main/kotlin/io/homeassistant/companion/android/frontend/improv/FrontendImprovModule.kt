package io.homeassistant.companion.android.frontend.improv

import android.content.Context
import android.content.pm.PackageManager
import com.wifi.improv.ImprovManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the frontend's Improv (Wi-Fi onboarding for BLE devices) integration.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FrontendImprovModule {

    @Binds
    @Singleton
    abstract fun bindImprovRepository(impl: ImprovRepositoryImpl): ImprovRepository

    companion object {
        @Provides
        fun provideBluetoothCapabilities(packageManager: PackageManager): BluetoothCapabilities =
            BluetoothCapabilities {
                packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            }

        @Provides
        @Singleton
        fun provideImprovManagerFactory(@ApplicationContext context: Context): ImprovManagerFactory =
            ImprovManagerFactory { callback -> ImprovManager(context.applicationContext, callback) }
    }
}
