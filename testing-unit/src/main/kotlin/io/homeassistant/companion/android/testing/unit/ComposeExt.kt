package io.homeassistant.companion.android.testing.unit

import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

fun AndroidComposeTestRule<*, *>.stringResources(@StringRes id: Int): String = activity.getString(id)
