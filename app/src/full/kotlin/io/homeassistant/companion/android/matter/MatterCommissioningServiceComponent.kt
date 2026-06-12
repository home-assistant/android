package io.homeassistant.companion.android.matter

import javax.inject.Qualifier

/**
 * Qualifier for the `ComponentName` pointing at [MatterCommissioningService].
 *
 * Disambiguates from any other `ComponentName` that might be injected in the same graph; see
 * [MatterPlayServicesModule] for the provider.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MatterCommissioningServiceComponent
