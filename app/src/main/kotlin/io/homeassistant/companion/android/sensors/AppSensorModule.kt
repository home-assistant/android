package io.homeassistant.companion.android.sensors

import android.app.PendingIntent
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorSettingsIntentProvider
import io.homeassistant.companion.android.sensors.generated.GeneratedProvidesSensorApp
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Singleton

/**
 * Hilt bindings for the `:app` sensor managers: each `:app/main` [SensorManager] contributed into the
 * `Set<SensorManager>` multibinding, plus the `:app` slice of the `Set<BasicSensor>` catalog.
 *
 * This Hilt module is hand-written (not generated) on purpose: Hilt's KSP processor does not
 * reliably aggregate an `@InstallIn` module that is itself *generated* by another KSP processor (the
 * KSP2 round model doesn't feed it to Hilt). So the provides-sensor processor generates only a plain
 * data object ([GeneratedProvidesSensorApp]), and this source module turns it into the binding.
 *
 * `:automotive` reuses this source set, so it must also generate `GeneratedProvidesSensorApp` (its
 * `providesSensorModuleSuffix` is set to `app`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppSensorModule {
    @Binds @IntoSet
    abstract fun bindAppSensorManager(impl: AppSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindCarSensorManager(impl: CarSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindDevicePolicyManager(impl: DevicePolicyManager): SensorManager

    @Binds @IntoSet
    abstract fun bindDynamicColorSensorManager(impl: DynamicColorSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindGeocodeSensorManager(impl: GeocodeSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindHealthConnectSensorManager(impl: HealthConnectSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindLastAppSensorManager(impl: LastAppSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindNotificationSensorManager(impl: NotificationListenerSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindQuestSensorManager(impl: QuestSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindActivitySensorManager(impl: ActivitySensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindAndroidAutoSensorManager(impl: AndroidAutoSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindLocationSensorManager(impl: LocationSensorManager): SensorManager

    companion object {
        @Provides
        @ElementsIntoSet
        fun appProvidesSensors(): Set<SensorManager.BasicSensor> = GeneratedProvidesSensorApp.sensors

        @Provides
        @Singleton
        fun providesSensorSettingsIntentProvider(): SensorSettingsIntentProvider = SensorSettingsIntentProvider {
                context,
                sensorId,
                _,
                notificationId,
            ->
            val intent = SettingsActivity.newInstance(context, SettingsActivity.Deeplink.Sensor(sensorId)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
