package io.homeassistant.companion.android.util.icondialog

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.util.HaIconTypeface

const val MDI_PREFIX = "mdi:"
const val MDI_HOME_ASSISTANT = "mdi:home-assistant"

/**
 * Gets the MDI name of an Iconics icon.
 * MDI format is used by Home Assistant (ie "mdi:account-alert"),
 * compared to Iconic's [IIcon.name] format (ie "cmd_account_alert").
 */
val IIcon.mdiName: String
    get() = name.replace("${CommunityMaterial.mappingPrefix}_", MDI_PREFIX).replace('_', '-')

fun CommunityMaterial.getIconByMdiName(mdiName: String): IIcon? {
    val name = mdiName.replace(MDI_PREFIX, "${mappingPrefix}_").replace('-', '_')

    if (mdiName == MDI_HOME_ASSISTANT) {
        return HaIconTypeface.Icon.mdi_home_assistant
    }

    return try {
        getIcon(name)
    } catch (e: IllegalArgumentException) {
        // Icon doesn't exist (anymore)
        null
    }
}
