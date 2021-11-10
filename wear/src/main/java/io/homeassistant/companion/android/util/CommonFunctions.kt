package io.homeassistant.companion.android.util

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R

@Composable
fun SetTitle(id: Int) {
    Text(
        text = stringResource(id = id),
        textAlign = TextAlign.Center,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
    )
}

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
