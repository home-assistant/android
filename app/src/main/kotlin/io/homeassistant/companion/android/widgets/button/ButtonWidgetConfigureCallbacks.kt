package io.homeassistant.companion.android.widgets.button

import io.github.timoptr.mdiicons.MdiIcon
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder

internal data class ButtonWidgetConfigureCallbacks(
    val onActionTextUpdated: (String) -> Unit,
    val onServerSelected: (Int) -> Unit,
    val onDynamicFieldUpdated: (Int, String) -> Unit,
    val onIconSelected: (MdiIcon) -> Unit,
    val onAddFieldDialogOkClicked: (Int, ActionFieldBinder) -> Unit,
    val onLabelUpdated: (String) -> Unit,
    val onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    val onTextColorSelected: (String) -> Unit,
    val onRequireAuthenticationChecked: (Boolean) -> Unit,
)
