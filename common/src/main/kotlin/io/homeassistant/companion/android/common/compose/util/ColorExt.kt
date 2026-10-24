package io.homeassistant.companion.android.common.compose.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val LIGHT_SURFACE_LUMINANCE_THRESHOLD = 0.5f

/** Whether this color is light enough that dark foreground icons are needed for contrast. */
fun Color.isLight(): Boolean = luminance() >= LIGHT_SURFACE_LUMINANCE_THRESHOLD
