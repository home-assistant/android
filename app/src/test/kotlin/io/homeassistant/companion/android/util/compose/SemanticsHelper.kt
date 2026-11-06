package io.homeassistant.companion.android.util.compose

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import io.homeassistant.companion.android.common.compose.composable.AlphaKey

fun SemanticsNodeInteraction.assertAlpha(alpha: Float): SemanticsNodeInteraction {
    return assert(SemanticsMatcher.expectValue(AlphaKey, alpha))
}

/** Two taps faster than a screen transition, like a user double tapping a button. */
fun SemanticsNodeInteraction.performTwoQuickClicks(): SemanticsNodeInteraction = performTouchInput {
    click()
    click()
}
