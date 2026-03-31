package io.homeassistant.companion.android.util.icondialog

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial

const val MDI_PREFIX = "mdi:"

/**
 * Gets the MDI name of an Iconics icon.
 * MDI format is used by Home Assistant (ie "mdi:account-alert"),
 * compared to Iconic's [IIcon.name] format (ie "cmd_account_alert").
 */
val IIcon.mdiName: String
    get() = name.replace("${CommunityMaterial.mappingPrefix}_", MDI_PREFIX).replace('_', '-')

fun CommunityMaterial.getIconByMdiName(mdiName: String): IIcon? {
    val name = mdiName.replace(MDI_PREFIX, "${mappingPrefix}_").replace('-', '_')
    return try {
        getIcon(name)
    } catch (e: IllegalArgumentException) {
        // Icon doesn't exist (anymore)
        null
    }
}
