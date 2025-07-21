package io.homeassistant.companion.android.compose.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HATopBar(onHelpClick: () -> Unit, onBackClick: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                )
            }
        },
        actions = {
            IconButton(onClick = onHelpClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(R.string.get_help),
                )
            }
        },
    )
}
