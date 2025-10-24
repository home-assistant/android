package io.homeassistant.companion.android

/**
 * Simple interface used to identify a route as being usable as start destination
 * for [io.homeassistant.companion.android.compose.HAApp].
 *
 * It is mainly used to enforce type when passing a route to the [androidx.navigation.NavHost].
 *
 * It could have been a sealed class but it would require moving the route definition to the HA compose
 * package, which might not be the best place for consistency and at the moment we don't need to list
 * all the potential destinations. Until we reach a blocker we don't need a sealed class.
 */
interface HAStartDestinationRoute
