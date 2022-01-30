package io.homeassistant.companion.android.util

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR

fun getIcon(icon: String?, domain: String, context: Context): IIcon? {
    return if (icon?.startsWith("mdi") == true) {
        val mdiIcon = icon.split(":")[1]
        IconicsDrawable(context, "cmd-$mdiIcon").icon
    } else {
        when (domain) {
            "cover" -> CommunityMaterial.Icon3.cmd_window_closed
            "fan" -> CommunityMaterial.Icon2.cmd_fan
            "input_boolean", "switch" -> CommunityMaterial.Icon2.cmd_light_switch
            "light" -> CommunityMaterial.Icon2.cmd_lightbulb
            "lock" -> CommunityMaterial.Icon2.cmd_lock
            "script" -> CommunityMaterial.Icon3.cmd_script_text_outline
            "scene" -> CommunityMaterial.Icon3.cmd_palette_outline
            else -> CommunityMaterial.Icon.cmd_cellphone
        }
    }
}

fun onEntityClickedFeedback(isToastEnabled: Boolean, isHapticEnabled: Boolean, context: Context, friendlyName: String, haptic: HapticFeedback) {
    if (isToastEnabled)
        Toast.makeText(context, context.getString(commonR.string.toast_message, friendlyName), Toast.LENGTH_SHORT).show()
    if (isHapticEnabled)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}
