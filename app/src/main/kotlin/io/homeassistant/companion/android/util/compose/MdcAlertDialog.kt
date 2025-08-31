package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.homeassistant.companion.android.common.R as commonR

/**
 * A dialog similar in style to a classic AlertDialog when using a Material Components theme, mostly
 * following the design specification but with minor differences to keep the code simple. When
 * compared to [androidx.compose.material.AlertDialog], the main differences for this dialog are:
 * - Different text styles by default that better match the classic AlertDialog
 * - Contents can use the full dialog width (for example for touch highlights)
 * - Content height is limited to screen size to make sure you cannot scroll the entire dialog,
 *   to make sure content can be scrolled you should probably have a LazyColumn as the content root
 * - Buttons are already defined and can only be set to show/hide to improve consistency
 *
 * @param onDismissRequest Action when the user dismisses the dialog by tapping outside it or pressing back
 * @param title Title for the dialog, provided text style will be [androidx.compose.material.Typography.h6].
 * @param content Content for the dialog, provided text style will be [androidx.compose.material.Typography.body1].
 * @param onCancel Action when the 'Cancel' button is pressed. Set to null to hide button.
 * @param onSave Action when the 'Save' button is pressed. Set to null to hide button.
 * @param contentPadding PaddingValues for the content. By default content will be padded to match the title and buttons.
 */
@Composable
fun MdcAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    onCancel: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onOK: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface,
        ) {
            Column(
                modifier = Modifier.heightIn(max = screenHeight() - 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                ) {
                    ProvideTextStyle(MaterialTheme.typography.h6, title)
                }
                Box(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxWidth()
                        .weight(weight = 1f, fill = false),
                ) {
                    ProvideTextStyle(MaterialTheme.typography.body1, content)
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 8.dp),
                ) {
                    onCancel?.let {
                        TextButton(onClick = it) {
                            Text(stringResource(commonR.string.cancel))
                        }
                    }
                    if (onCancel != null && onSave != null) Spacer(modifier = Modifier.width(8.dp))
                    onSave?.let {
                        TextButton(onClick = it) {
                            Text(stringResource(commonR.string.save))
                        }
                    }
                    if ((onCancel != null || onSave != null) && onOK != null) Spacer(modifier = Modifier.width(8.dp))
                    onOK?.let {
                        TextButton(onClick = it) {
                            Text(stringResource(commonR.string.ok))
                        }
                    }
                }
            }
        }
    }
}
