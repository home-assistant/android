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
import androidx.core.view.updatePadding
import androidx.preference.PreferenceFragmentCompat

@Composable
fun WindowInsets.bottomPaddingValues(): PaddingValues {
    return only(WindowInsetsSides.Bottom).asPaddingValues()
}

fun PreferenceFragmentCompat.applyBottomSystemBarsInsets(consumeInsets: Boolean = true) {
    listView.applySystemBarsInsets(
        applyLeft = false,
        applyTop = false,
        applyRight = false,
        applyBottom = true,
        consumeInsets = consumeInsets,
    )
}

fun View.applyBottomSystemBarsInsets(consumeInsets: Boolean = true) {
    applySystemBarsInsets(
        applyLeft = false,
        applyTop = false,
        applyRight = false,
        applyBottom = true,
        consumeInsets = consumeInsets,
    )
}

fun View.applySystemBarsInsets(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
    consumeInsets: Boolean = true,
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insetsSystemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

        view.updatePadding(
            left = if (applyLeft) insetsSystemBars.left else paddingLeft,
            top = if (applyTop) insetsSystemBars.top else paddingTop,
            right = if (applyRight) insetsSystemBars.right else paddingRight,
            bottom = if (applyBottom) insetsSystemBars.bottom else paddingBottom,
        )

        if (consumeInsets) WindowInsetsCompat.CONSUMED else windowInsets
    }
}
