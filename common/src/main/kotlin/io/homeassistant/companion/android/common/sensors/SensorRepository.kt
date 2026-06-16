package io.homeassistant.companion.android.common.sensors

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single access point for sensor state and the sensor catalog.
 *
 * Stub: for now it only receives the generated [SensorManager.BasicSensor] catalog (built by the
 * sensor-catalog KSP processor from `@CatalogSensor` annotations) and exposes it. The catalog/DB
 * merge, effective-state reads, and writes will be added in a later stage.
 */
@Singleton
class SensorRepository @Inject constructor(val basicSensors: Set<@JvmSuppressWildcards SensorManager.BasicSensor>)
