package io.homeassistant.companion.android.frontend.improv

import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt bindings for the frontend's improv (Wi-Fi onboarding for BLE devices) integration.
 */
@Module
@InstallIn(SingletonComponent::class)
object FrontendImprovModule {

    @Provides
    fun provideBluetoothCapabilities(packageManager: PackageManager): BluetoothCapabilities = BluetoothCapabilities {
        packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
}
