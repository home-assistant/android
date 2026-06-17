package io.homeassistant.companion.android.database

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.settings.SettingsDao

/**
 * Hilt EntryPoint for accessing database DAOs and the sensor repository in classes that cannot use
 * constructor injection.
 *
 * Use this for BroadcastReceivers, Services, SensorManagers, and other classes where
 * Hilt's automatic injection is not available.
 *
 * Usage:
 * ```
 * val entryPoint = DatabaseEntryPoint.resolve(context)
 * val sensorRepository = entryPoint.sensorRepository()
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun sensorRepository(): SensorRepository
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
