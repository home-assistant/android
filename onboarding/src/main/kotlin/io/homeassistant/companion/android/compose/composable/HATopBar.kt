package io.homeassistant.companion.android.compose.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R
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
                        contentDescription = stringResource(R.string.navigate_up),
                    )
                }
            }
        },
        actions = {
            onHelpClick?.let {
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.get_help),
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
