package io.homeassistant.companion.android.common.sensors

/**
 * Marks a [SensorManager.BasicSensor] val for inclusion in the generated `Set<BasicSensor>` catalog
 * (see the sensor-catalog KSP processor). Every `BasicSensor` val must be annotated; an
 * error-severity lint rule (`CatalogSensorMissing`) flags omissions. To intentionally exclude a
 * `BasicSensor` from the catalog, leave it un-annotated and `@Suppress("CatalogSensorMissing")`.
 *
 * The annotated val must be statically referenceable (top-level, `object`, or `companion object`).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class CatalogSensor
