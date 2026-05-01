package io.homeassistant.companion.android.di.qualifiers

import javax.inject.Qualifier

/**
 * Qualifier for [LocalStorage] dependencies for managing **session-specific data**.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedSessionStorage

/**
 * Qualifier for [LocalStorage] dependencies related to Home Assistant integration.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedIntegrationStorage

/**
 * Qualifier for [LocalStorage] dependencies related to application themes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedThemesStorage

/**
 * Qualifier for [LocalStorage] dependencies specific to Wear OS functionality.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedWearStorage

/**
 * Qualifier for [LocalStorage] dependencies that persist Health Connect Changes API tokens
 * (one per data type) so the changes worker can resume from a known position across process
 * restarts without redoing a full sensor sweep.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedHealthConnectStorage

/**
 * Qualifier for a [String] dependency providing device manufacturer information.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedManufacturer

/**
 * Qualifier for a [String] dependency providing device model information.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedModel

/**
 * Qualifier for a [String] dependency providing the device OS version.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedOsVersion

/**
 * Qualifier for a [String] dependency providing the unique device ID.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedDeviceId

/**
 * Qualifier for a [SuspendProvider<String>] dependency providing the unique install ID.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NamedInstallId

/**
 * Qualifier for dependencies related to location tracking support functionality.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocationTrackingSupport

/**
 * Qualifier for a [Boolean] dependency indicating whether the device is an Android Automotive device.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsAutomotive
