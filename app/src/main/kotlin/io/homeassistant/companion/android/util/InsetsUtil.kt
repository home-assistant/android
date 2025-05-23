package io.homeassistant.companion.android.util

import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.preference.PreferenceFragmentCompat

@Composable
fun WindowInsets.bottomPaddingValues(): PaddingValues {
    return only(WindowInsetsSides.Bottom).asPaddingValues()
}

fun PreferenceFragmentCompat.applyBottomSafeDrawingInsets(consumeInsets: Boolean = true) {
    listView.applySafeDrawingInsets(
        applyLeft = false,
        applyTop = false,
        applyRight = false,
        applyBottom = true,
        consumeInsets = consumeInsets,
    )
}

fun View.applyBottomSafeDrawingInsets(consumeInsets: Boolean = true) {
    applySafeDrawingInsets(
        applyLeft = false,
        applyTop = false,
        applyRight = false,
        applyBottom = true,
        consumeInsets = consumeInsets,
    )
}

fun View.applySafeDrawingInsets(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
    consumeInsets: Boolean = true,
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insetsSystemBars = windowInsets.getInsets(
            ime() + systemBars() + displayCutout(),
        )

        view.updatePadding(
            left = if (applyLeft) insetsSystemBars.left else paddingLeft,
            top = if (applyTop) insetsSystemBars.top else paddingTop,
            right = if (applyRight) insetsSystemBars.right else paddingRight,
            bottom = if (applyBottom) insetsSystemBars.bottom else paddingBottom,
        )

        if (consumeInsets) WindowInsetsCompat.CONSUMED else windowInsets
    }
}
