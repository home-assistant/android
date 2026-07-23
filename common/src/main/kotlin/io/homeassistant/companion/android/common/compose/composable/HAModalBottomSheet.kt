package io.homeassistant.companion.android.common.compose.composable

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Velocity
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Remembers a [SheetState] for use with [HAModalBottomSheet].
 *
 * @param skipPartiallyExpanded Whether the runtime modal sheet should always open fully expanded.
 *
 * In inspection mode (previews and screenshot tests), this returns a [rememberStandardBottomSheetState]
 * because [rememberModalBottomSheetState] requires a fully running Compose runtime, and [rememberStandardBottomSheetState]
 * doesn't animate properly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHAModalBottomSheetState(skipPartiallyExpanded: Boolean = false): SheetState =
    if (LocalInspectionMode.current) {
        rememberStandardBottomSheetState(skipHiddenState = false)
    } else {
        rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
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
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        modifier = modifier,
        sheetState = bottomSheetState,
        scrimColor = LocalHAColorScheme.current.colorOverlayModal,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = HARadius.X3L, topEnd = HARadius.X3L),
        dragHandle = dragHandle,
        content = content,
    )
}

/**
 * Prevents the enclosing [HAModalBottomSheet] from collapsing while its content scrolls or flings.
 *
 * Stacks two safeguards on the receiver:
 *   * a [NestedScrollConnection] that absorbs leftover scroll deltas and fling velocity at the
 *     content boundary so the sheet's drag handler never sees them; and
 *   * a [pointerInput] block that swallows raw vertical drag gestures originating on non-scrolling
 *     children (headers, fixed footers) which the sheet would otherwise treat as collapse swipes.
 *
 * Apply to the root [Modifier] of any scrollable / footer-bearing column hosted inside a modal
 * bottom sheet.
 */
fun Modifier.consumeSheetScrollFling(): Modifier = this
    .nestedScroll(ConsumeSheetScrollFlingConnection)
    .pointerInput(Unit) {
        detectVerticalDragGestures { _, _ -> }
    }

/**
 * Stateless [NestedScrollConnection] used by [consumeSheetScrollFling].
 */
private val ConsumeSheetScrollFlingConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
