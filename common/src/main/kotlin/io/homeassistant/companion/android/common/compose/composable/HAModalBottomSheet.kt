package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Remembers a [SheetState] for use with [HAModalBottomSheet].
 *
 * In inspection mode (previews and screenshot tests), this returns a [rememberStandardBottomSheetState]
 * because [rememberModalBottomSheetState] requires a fully running Compose runtime, and [rememberStandardBottomSheetState]
 * doesn't animate properly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHAModalBottomSheetState(): SheetState = if (LocalInspectionMode.current) {
    rememberStandardBottomSheetState(skipHiddenState = false)
} else {
    rememberModalBottomSheetState()
}

/**
 * A modal bottom sheet that uses the Home Assistant theme.
 *
 * @param bottomSheetState The state of the bottom sheet.
 * @param modifier Optional [Modifier] for this bottom sheet.
 * @param onDismissRequest Called when the user attempts to dismiss the bottom sheet.
 * @param content The content of the bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HAModalBottomSheet(
    bottomSheetState: SheetState,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        modifier = modifier,
        sheetState = bottomSheetState,
        scrimColor = LocalHAColorScheme.current.colorOverlayModal,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = HARadius.X3L, topEnd = HARadius.X3L),
        content = content,
    )
}
