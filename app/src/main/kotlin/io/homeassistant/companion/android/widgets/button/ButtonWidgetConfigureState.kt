package io.homeassistant.companion.android.widgets.button

import androidx.compose.runtime.Stable
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Flash
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureState
import io.homeassistant.companion.android.widgets.configure.defaultWidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.dynamicColorAvailable

@Stable
internal data class ButtonWidgetConfigureState(
    override val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val actionText: String = "",
    val serverActions: List<Action> = emptyList(),
    val dynamicFields: List<ActionFieldBinder> = emptyList(),
    val domainEntities: List<String> = emptyList(),
    val selectedIcon: MdiIcon = Mdi.Flash,
    val selectedIconId: String? = null,
    val label: String = "",
    val selectedBackgroundType: WidgetBackgroundType = defaultWidgetBackgroundType(),
    val textColorHex: String? = null,
    val requiresAuthentication: Boolean = false,
    val dynamicColorAvailable: Boolean = dynamicColorAvailable(),
    override val isUpdateWidget: Boolean = false,
) : WidgetConfigureState<ButtonWidgetConfigureState> {

    override val isActionEnabled: Boolean
        get() = actionText.isNotBlank()

    override fun withServersDropdownItems(items: List<HADropdownItem<Int>>) = copy(serversDropdownItems = items)

    override fun withSelectedServerId(serverId: Int) = copy(selectedServerId = serverId)

    override fun withBackgroundType(backgroundType: WidgetBackgroundType) =
        copy(selectedBackgroundType = backgroundType)

    override fun changeServer(serverId: Int) = copy(selectedServerId = serverId, actionText = "")
}
