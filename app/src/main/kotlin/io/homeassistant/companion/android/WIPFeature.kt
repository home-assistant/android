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
object WIPFeature {

    /**
     * Enables the new frontend screen using compose navigation instead of the WebViewActivity.
     *
     * When true, [FrontendRoute] navigates to [FrontendScreen] (Compose).
     * When false, [FrontendRoute] navigates to [io.homeassistant.companion.android.webview.WebViewActivity] (legacy).
     *
     * This flag is only enabled in DEBUG builds during development.
     */
    val USE_FRONTEND_V2: Boolean = BuildConfig.DEBUG
}
