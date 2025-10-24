package io.homeassistant.companion.android.compose

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert

fun SemanticsNodeInteraction.assertAlpha(alpha: Float): SemanticsNodeInteraction {
    return assert(SemanticsMatcher.expectValue(AlphaKey, alpha))
}
