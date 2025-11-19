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
