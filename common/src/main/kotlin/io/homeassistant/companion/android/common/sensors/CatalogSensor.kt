package io.homeassistant.companion.android.common.sensors

/**
 * Marks a [SensorManager.BasicSensor] val for inclusion in the generated `Set<BasicSensor>` catalog
 * (see the sensor-catalog KSP processor). Every `BasicSensor` val must be annotated; an
 * error-severity lint rule (`CatalogSensorMissing`) flags omissions. To intentionally exclude a
 * `BasicSensor` from the catalog, leave it un-annotated and `@Suppress("CatalogSensorMissing")`.
 *
 * The val must be statically referenceable so the generated catalog can reach it: a non-private val
 * that is top-level, in an `object`, or in a `companion object`. The processor reports a compile
 * error otherwise.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class CatalogSensor
