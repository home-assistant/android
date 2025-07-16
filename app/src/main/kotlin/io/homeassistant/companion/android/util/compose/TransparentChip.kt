package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon

/**
 * A Material 3-style Assist Chip with a transparent background
 */
@Composable
fun TransparentChip(modifier: Modifier = Modifier, text: String, icon: IIcon? = null, onClick: () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = Color.LightGray,
        ),
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Image(
                    asset = icon,
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(18.dp),
                )
            }
            Text(
                text = text,
                color = contentColorFor(MaterialTheme.colors.onSurface),
                style = MaterialTheme.typography.body2,
                modifier =
                if (icon != null) {
                    Modifier.padding(end = 16.dp)
                } else {
                    Modifier.padding(horizontal = 16.dp)
                },
            )
        }
    }
}
