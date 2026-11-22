package io.homeassistant.companion.android.testing.unit

import android.content.Context
import android.provider.Settings

/** Fake `Settings.Secure.ANDROID_ID` seeded by [seedFakeAndroidId]. */
const val FAKE_ANDROID_ID = "robolectric-android-id"

/**
 * Seeds [Settings.Secure.ANDROID_ID] with [FAKE_ANDROID_ID] on this context.
 *
 * Robolectric leaves the value null, but the integration graph injects it as a non-null
 * `@NamedDeviceId`, so any test that builds that graph must seed it first to avoid a
 * null-from-`@Provides` crash during dependency injection.
 */
fun Context.seedFakeAndroidId() {
    Settings.Secure.putString(contentResolver, Settings.Secure.ANDROID_ID, FAKE_ANDROID_ID)
}
