package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.common.BuildConfig

/**
 * Sanitizes sensitive data for logging by returning the actual value in a secure environment
 * and "HIDDEN" otherwise, preventing user data from leaking into production logs.
 *
 * This should be used instead of manual `if (BuildConfig.DEBUG)` checks in Timber log messages
 * whenever the log contains data that could identify the user or their Home Assistant setup
 * (URLs, tokens, message payloads, etc.).
 *
 * ```kotlin
 * Timber.d("Load url=${sensitive(url)}")
 * // Debug:   "Load url=https://my-ha.example.com/api"
 * // Release: "Load url=HIDDEN"
 * ```
 *
 * @param sensitive the value to sanitize
 * @return the original value when it is secure, "HIDDEN" otherwise
 */
fun sensitive(sensitive: String): String = sensitive.takeIf { BuildConfig.DEBUG } ?: "HIDDEN"
