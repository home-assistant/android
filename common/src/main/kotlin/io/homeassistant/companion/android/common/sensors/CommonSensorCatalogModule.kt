package io.homeassistant.companion.android.common.sensors

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import io.homeassistant.companion.android.sensorcatalog.generated.GeneratedSensorCatalogCommon

/**
 * Contributes the `:common` module's `@CatalogSensor` sensors into the `Set<BasicSensor>` catalog.
 *
 * This Hilt module is hand-written (not generated) on purpose: Hilt's KSP processor does not
 * reliably aggregate an `@InstallIn` module that is itself *generated* by another KSP processor (the
 * KSP2 round model doesn't feed it to Hilt). So the sensor-catalog processor generates only a plain
 * data object ([GeneratedSensorCatalogCommon]), and this source module turns it into the binding.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object CommonSensorCatalogModule {
    @Provides
    @ElementsIntoSet
    fun commonCatalogSensors(): Set<SensorManager.BasicSensor> = GeneratedSensorCatalogCommon.sensors
}
