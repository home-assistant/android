package io.homeassistant.companion.android.sensors

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.sensors.generated.GeneratedProvidesSensorWear

/**
 * Hilt bindings for the `:wear` sensor managers: each wear-specific [SensorManager] contributed into
 * the `Set<SensorManager>` multibinding, plus the `:wear` slice of the `Set<BasicSensor>` catalog.
 *
 *
 * This Hilt module is hand-written (not generated) on purpose: Hilt's KSP processor does not
 * reliably aggregate an `@InstallIn` module that is itself *generated* by another KSP processor (the
 * KSP2 round model doesn't feed it to Hilt). So the provides-sensor processor generates only a plain
 * data object ([GeneratedProvidesSensorWear]), and this source module turns it into the binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearSensorModule {
    @Binds @IntoSet
    abstract fun bindAppSensorManager(impl: AppSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindBedtimeModeSensorManager(impl: BedtimeModeSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindHealthServicesSensorManager(impl: HealthServicesSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindHeartRateSensorManager(impl: HeartRateSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindOnBodySensorManager(impl: OnBodySensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindTheaterModeSensorManager(impl: TheaterModeSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindWetModeSensorManager(impl: WetModeSensorManager): SensorManager

    companion object {
        @Provides
        @ElementsIntoSet
        fun wearProvidesSensors(): Set<SensorManager.BasicSensor> = GeneratedProvidesSensorWear.sensors
    }
}
