package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.safeBottomWindowInsets

/**
 * A Material 3-style modal bottom sheet with an optional handle, for use with a
 * [com.google.android.material.bottomsheet.BottomSheetDialogFragment].
 */
@Composable
fun ModalBottomSheet(title: String?, showHandle: Boolean = true, content: @Composable () -> Unit) {
    val sheetCornerRadius = dimensionResource(R.dimen.bottom_sheet_corner_radius)
    Surface(
        shape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeBottomWindowInsets(false)),
        ) {
            if (showHandle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colorResource(commonR.color.colorBottomSheetHandle)),
                    )
                }
            }
            if (title != null) {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 22.dp),
                    style = MaterialTheme.typography.h6,
                    textAlign = TextAlign.Center,
                )
            }
            content()
        }
    }
}
