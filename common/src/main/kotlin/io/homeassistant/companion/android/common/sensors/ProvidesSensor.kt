package io.homeassistant.companion.android.common.sensors

/**
 * Marks a [SensorManager.BasicSensor] val for inclusion in the generated `Set<BasicSensor>`
 * (see the provides-sensor KSP processor). Every `BasicSensor` val must be annotated; an
 * error-severity lint rule (`ProvidesSensorMissing`) flags omissions. To intentionally exclude a
 * `BasicSensor` from being provided, leave it un-annotated and `@Suppress("ProvidesSensorMissing")`.
 *
 * The val must be statically referenceable so the generated code can reach it: a non-private val
 * that is top-level, in an `object`, or in a `companion object`. The processor reports a compile
 * error otherwise.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ProvidesSensor
