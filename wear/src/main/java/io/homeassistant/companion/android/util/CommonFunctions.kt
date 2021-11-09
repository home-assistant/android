package io.homeassistant.companion.android.util

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.colorResource
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R

@Composable
fun setChipDefaults(): ChipColors {
    return ChipDefaults.primaryChipColors(
        backgroundColor = colorResource(id = R.color.colorAccent),
        contentColor = Color.Black
    )
}

fun getIcon(icon: String?, domain: String, context: Context): IIcon? {
    return if (icon?.startsWith("mdi") == true) {
        val mdiIcon = icon.split(":")[1]
        IconicsDrawable(context, "cmd-$mdiIcon").icon
    } else {
        when (domain) {
            "input_boolean", "switch" -> CommunityMaterial.Icon2.cmd_light_switch
            "light" -> CommunityMaterial.Icon2.cmd_lightbulb
            "script" -> CommunityMaterial.Icon3.cmd_script_text_outline
            "scene" -> CommunityMaterial.Icon3.cmd_palette_outline
            else -> CommunityMaterial.Icon.cmd_cellphone
        }
    }
}

fun onEntityClickedFeedback(isToastEnabled: Boolean, isHapticEnabled: Boolean, context: Context, friendlyName: String, haptic: HapticFeedback) {
    if (isToastEnabled)
        Toast.makeText(context, context.getString(R.string.toast_message, friendlyName), Toast.LENGTH_SHORT).show()
    if (isHapticEnabled)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
}
