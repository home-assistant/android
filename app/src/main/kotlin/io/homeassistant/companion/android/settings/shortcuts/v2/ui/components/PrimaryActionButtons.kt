package io.homeassistant.companion.android.settings.shortcuts.v2.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

@Composable
internal fun PrimaryActionButtons(
    isEditing: Boolean,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val labelRes = if (isEditing) R.string.update_shortcut else R.string.add_shortcut

    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            if (isEditing && onDelete != null) {
                HAFilledButton(
                    text = stringResource(R.string.delete_shortcut),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.DANGER,
                )
            }

            HAFilledButton(
                text = stringResource(labelRes),
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(name = "Primary Action Buttons")
@Composable
private fun PrimaryActionButtonsPreview() {
    HAThemeForPreview {
        PrimaryActionButtons(
            isEditing = true,
            canSubmit = true,
            onSubmit = {},
            onDelete = {},
        )
    }
}
