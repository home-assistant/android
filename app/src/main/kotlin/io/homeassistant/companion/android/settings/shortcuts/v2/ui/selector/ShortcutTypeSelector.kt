package io.homeassistant.companion.android.settings.shortcuts.v2.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType

@Composable
internal fun ShortcutTypeSelector(type: ShortcutType, onTypeChange: (ShortcutType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        Text(
            text = stringResource(R.string.shortcut_type),
            style = HATextStyle.Body,
        )

        HARadioGroup(
            spaceBy = HADimens.SPACE3,
            options = listOf(
                RadioOption(
                    selectionKey = ShortcutType.LOVELACE,
                    headline = stringResource(R.string.lovelace),
                ),
                RadioOption(
                    selectionKey = ShortcutType.ENTITY_ID,
                    headline = stringResource(R.string.entity),
                ),
            ),
            selectionKey = type,
            onSelect = { selected -> onTypeChange(selected.selectionKey) },
        )
    }
}

@Preview(name = "Shortcut Type Selector")
@Composable
private fun ShortcutTypeSelectorPreview() {
    HAThemeForPreview {
        ShortcutTypeSelector(
            type = ShortcutType.LOVELACE,
            onTypeChange = {},
        )
    }
}
