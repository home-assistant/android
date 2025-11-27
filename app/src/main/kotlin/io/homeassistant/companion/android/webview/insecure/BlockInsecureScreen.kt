package io.homeassistant.companion.android.webview.insecure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.util.compose.HAPreviews
import io.homeassistant.companion.android.util.compose.rememberLocationPermission

private val MaxContentWidth = MaxButtonWidth

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun BlockInsecureScreen(
    missingHomeSetup: Boolean,
    missingLocation: Boolean,
    onRetry: () -> Unit,
    onHelpClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onChangeSecurityLevel: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfigureHomeNetwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locationPermissions = rememberLocationPermission(
        onPermissionResult = {
            onOpenLocationSettings()
        },
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopBar(onRetry = onRetry, onHelpClick = onHelpClick) },
    ) { contentPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HADimens.SPACE4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
            ) {
                Header()

                if (missingLocation) {
                    FixBanner(
                        text = stringResource(commonR.string.block_insecure_missing_location),
                        actionText = stringResource(commonR.string.block_insecure_action_enable_location),
                        onFixClick = locationPermissions::launchMultiplePermissionRequest,
                    )
                }

                if (missingHomeSetup) {
                    FixBanner(
                        text = stringResource(commonR.string.block_insecure_missing_home_setup),
                        actionText = stringResource(commonR.string.block_insecure_action_configure_home),
                        onFixClick = onConfigureHomeNetwork,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                BottomButtons(onOpenSettings = onOpenSettings, onChangeSecurityLevel = onChangeSecurityLevel)
            }
        }
    }
}

@Composable
private fun TopBar(onRetry: () -> Unit, onHelpClick: () -> Unit) {
    HATopBar(
        navigationIcon = {
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Outlined.Replay,
                    contentDescription = stringResource(commonR.string.block_insecure_retry),
                )
            }
        },
        actions = {
            IconButton(onClick = onHelpClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = stringResource(commonR.string.get_help),
                )
            }
        },
    )
}

@Composable
private fun ColumnScope.Header() {
    Icon(
        modifier = Modifier
            .padding(all = 20.dp)
            .size(120.dp),
        imageVector = Icons.Default.Lock,
        tint = LocalHAColorScheme.current.colorOnPrimaryNormal,
        contentDescription = null,
    )

    Text(
        text = stringResource(commonR.string.block_insecure_title),
        style = HATextStyle.Headline,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )

    Text(
        text = stringResource(commonR.string.block_insecure_content),
        style = HATextStyle.Body,
        modifier = Modifier.widthIn(max = MaxContentWidth),
    )
}

@Composable
private fun FixBanner(text: String, actionText: String, onFixClick: () -> Unit) {
    HABanner(
        modifier = Modifier
            .width(MaxContentWidth),
    ) {
        Column {
            Text(
                text = text,
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                HAAccentButton(
                    text = actionText,
                    variant = ButtonVariant.WARNING,
                    onClick = {
                        onFixClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomButtons(onOpenSettings: () -> Unit, onChangeSecurityLevel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
    ) {
        HAAccentButton(
            text = stringResource(commonR.string.block_insecure_open_settings),
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth(),
        )

        HAPlainButton(
            text = stringResource(commonR.string.block_insecure_change_security_level),
            onClick = onChangeSecurityLevel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HADimens.SPACE6),
        )
    }
}

@HAPreviews
@Composable
private fun BlockInsecureScreenPreview() {
    HAThemeForPreview {
        BlockInsecureScreen(
            missingHomeSetup = true,
            missingLocation = true,
            onRetry = {},
            onHelpClick = {},
            onOpenSettings = {},
            onChangeSecurityLevel = {},
            onOpenLocationSettings = {},
            onConfigureHomeNetwork = {},
        )
    }
}
