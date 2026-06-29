package io.homeassistant.companion.android.common.sensors

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single access point for sensor state and the set of provided sensors.
 *
 * TODO: For now it only receives the generated [SensorManager.BasicSensor] set (built by the
 * provides-sensor KSP processor from `@ProvidesSensor` annotations) and exposes it. The merge with
 * the DB, effective-state reads, and writes will be added in a later stage.
 */
@Singleton
class SensorRepository @Inject constructor(val basicSensors: Set<@JvmSuppressWildcards SensorManager.BasicSensor>)
