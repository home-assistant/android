package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Generic top bar for Home Assistant screens with optional buttons
 * - if onHelpClick is not null, a help button will be added
 * - if onBackClick is not null, a back button will be added
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HATopBar(
    title: @Composable () -> Unit = {},
    onHelpClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            onBackClick?.let {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(commonR.string.navigate_up),
                    )
                }
            }
        },
        actions = {
            onHelpClick?.let {
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(commonR.string.get_help),
                    )
                }
            }
        },
        colors = TopAppBarColors(
            containerColor = LocalHAColorScheme.current.colorSurfaceDefault,
            // TODO validate that we use colorOnNeutralQuiet
            navigationIconContentColor = LocalHAColorScheme.current.colorOnNeutralQuiet,
            actionIconContentColor = LocalHAColorScheme.current.colorOnNeutralQuiet,
            // For now this color are not used we would need to decide with Design team which token to use here
            scrolledContainerColor = Color.Unspecified,
            titleContentColor = LocalHAColorScheme.current.colorTextPrimary,
        ),
    )
}

/**
 * Creates a spacer with the same height as [HATopBar] to maintain consistent top spacing
 * across screens.
 *
 * Use this composable on screens without a top bar to ensure the content starts at the same
 * vertical position as screens that have a top bar. This provides visual consistency when
 * navigating between screens with and without top bars.
 *
 * @param modifier Optional [Modifier] to be applied to the spacer
 */
@Composable
internal fun HATopBarPlaceholder(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(64.dp - HADimens.SPACE6))
}
