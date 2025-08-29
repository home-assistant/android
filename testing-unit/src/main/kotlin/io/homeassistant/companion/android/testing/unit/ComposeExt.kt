package io.homeassistant.companion.android.testing.unit

import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/**
 * Helper extension to get a string from a resource on a [AndroidComposeTestRule].
 */
fun AndroidComposeTestRule<*, *>.stringResource(@StringRes id: Int): String = activity.getString(id)
