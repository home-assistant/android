package io.homeassistant.companion.android

/**
 * Central configuration for work-in-progress features.
 *
 * This file provides a single location to manage feature flags for features that are under active
 * development but not yet ready for production release. By centralizing these flags, we can:
 * - Easily enable/disable features for testing
 * - Maintain visibility of all in-progress work
 * - Easily find all the places that needs to be updated when we want to finalize the feature.
 *
 * Feature flags should be removed from this file once the feature is fully released and stable.
 */

/**
 * Enables the new onboarding from the `:onboarding` module.
 * Currently only enabled in debug builds to allow testing before production release.
 *
 * Features tracked in https://github.com/home-assistant/android/issues/5980
 */
val USE_NEW_LAUNCHER by lazy { BuildConfig.DEBUG }
