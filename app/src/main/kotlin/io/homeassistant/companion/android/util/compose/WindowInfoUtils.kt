package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

@Composable
fun screenHeight(): Dp {
    return with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
}

@Composable
fun safeScreenHeight(): Dp {
    val systemBarsPadding = WindowInsets.safeContent.asPaddingValues()
    return screenHeight() - systemBarsPadding.calculateTopPadding() - systemBarsPadding.calculateBottomPadding()
}

@Composable
fun screenWidth(): Dp {
    return with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
}
