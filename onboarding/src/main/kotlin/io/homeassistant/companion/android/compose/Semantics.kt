package io.homeassistant.companion.android.compose

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Custom compose semantics token to store alpha value of a composable.
 */
val AlphaKey = SemanticsPropertyKey<Float>("Alpha")
var SemanticsPropertyReceiver.alpha by AlphaKey
