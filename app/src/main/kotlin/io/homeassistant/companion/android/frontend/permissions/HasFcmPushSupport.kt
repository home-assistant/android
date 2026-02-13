package io.homeassistant.companion.android.frontend.permissions

import javax.inject.Qualifier

/**
 * Qualifier for a [Boolean] indicating whether the app has FCM (Firebase Cloud Messaging)
 * push notification support.
 *
 * This is `true` for the `full` flavor (Google Play Services available) and `false` for
 * the `minimal` flavor (FOSS version without Google Play Services).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class HasFcmPushSupport
