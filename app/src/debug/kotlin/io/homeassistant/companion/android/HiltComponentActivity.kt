package io.homeassistant.companion.android

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * A [androidx.activity.ComponentActivity] annotated with [dagger.hilt.android.AndroidEntryPoint] for use in tests, as a workaround
 * for https://github.com/google/dagger/issues/3394
 *
 * @see https://github.com/android/nowinandroid/blob/fffa52618510c4605766c83e6f5696045ec767b2/ui-test-hilt-manifest/src/main/kotlin/com/google/samples/apps/nowinandroid/uitesthiltmanifest/HiltComponentActivity.kt
 */
@AndroidEntryPoint
class HiltComponentActivity : ComponentActivity()
