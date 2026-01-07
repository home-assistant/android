package io.homeassistant.companion.android.database

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.settings.SettingsDao

/**
 * Hilt EntryPoint for accessing database DAOs in classes that cannot use constructor injection.
 *
 * Use this for BroadcastReceivers, Services, SensorManagers, and other classes where
 * Hilt's automatic injection is not available.
 *
 * Usage:
 * ```
 * val entryPoint = DatabaseEntryPoint.resolve(context)
 * val sensorDao = entryPoint.sensorDao()
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun sensorDao(): SensorDao
    fun settingsDao(): SettingsDao
    fun notificationDao(): NotificationDao

    companion object {
        /**
         * Resolves the EntryPoint from an application context.
         *
         * @param context Any context - will be converted to application context internally
         * @return The DatabaseEntryPoint for accessing DAOs
         */
        fun resolve(context: Context): DatabaseEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DatabaseEntryPoint::class.java,
        )
    }
}
