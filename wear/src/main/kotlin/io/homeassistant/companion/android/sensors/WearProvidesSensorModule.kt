package io.homeassistant.companion.android.sensors

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.sensors.generated.GeneratedProvidesSensorWear

/**
 * Contributes the `:wear` module's `@ProvidesSensor` sensors into the `Set<BasicSensor>`.
 *
 * This Hilt module is hand-written (not generated) on purpose: Hilt's KSP processor does not
 * reliably aggregate an `@InstallIn` module that is itself *generated* by another KSP processor (the
 * KSP2 round model doesn't feed it to Hilt). So the provides-sensor processor generates only a plain
 * data object ([GeneratedProvidesSensorWear]), and this source module turns it into the binding.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WearProvidesSensorModule {
    @Provides
    @ElementsIntoSet
    fun wearProvidesSensors(): Set<SensorManager.BasicSensor> = GeneratedProvidesSensorWear.sensors
}
