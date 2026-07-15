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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * A Composable that displays a typical Material Design clickable list item
 * with a title, subtitle, and icon slot (from the MDI library).
 */
@Composable
fun SettingsRow(
    primaryText: String,
    secondaryText: String,
    mdiIcon: IIcon?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClicked: () -> Unit,
) {
    SettingsRow(
        primaryText = primaryText,
        secondaryText = secondaryText,
        modifier = modifier,
        icon = {
            if (mdiIcon != null) {
                Image(
                    asset = mdiIcon,
                    modifier = Modifier
                        .size(DefaultIconSize)
                        .alpha(if (enabled) 1f else 0.38f),
                    colorFilter = ColorFilter.tint(
                        if (enabled) {
                            LocalHAColorScheme.current.colorOnNeutralQuiet
                        } else {
                            LocalHAColorScheme.current.colorOnNeutralQuiet.copy(alpha = 0.38f)
                        },
                    ),
                )
            } else {
                Spacer(modifier = Modifier.width(DefaultIconSize))
            }
            // Spacer to reach 72dp grid line from start
            Spacer(modifier = Modifier.width(72.dp - 16.dp - DefaultIconSize))
        },
        onClicked = onClicked,
    )
}

/**
 * A Composable that displays a typical Material Design clickable list item
 * with a title and subtitle.
 */
@Composable
fun SettingsRow(
    primaryText: String,
    secondaryText: String,
    icon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    onClicked: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable { onClicked() }
            .heightIn(min = 72.dp)
            .padding(all = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
        }
        Column(
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalHAColorScheme.current.colorTextPrimary,
            )
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalHAColorScheme.current.colorTextSecondary,
            )
        }
    }
}

private val DefaultIconSize = 24.dp
