package io.homeassistant.companion.android.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon

@Composable
fun SettingsRow(
    primaryText: String,
    secondaryText: String,
    mdiIcon: IIcon?,
    enabled: Boolean,
    onClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClicked() }
            .heightIn(min = 72.dp)
            .padding(all = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mdiIcon != null) {
            Image(
                asset = mdiIcon,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (enabled) ContentAlpha.high else ContentAlpha.disabled),
                colorFilter = ColorFilter.tint(
                    if (enabled) {
                        MaterialTheme.colors.primary
                    } else {
                        contentColorFor(backgroundColor = MaterialTheme.colors.background)
                    }
                )
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
        Spacer(modifier = Modifier.width(32.dp))
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.body1
            )
            CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}
