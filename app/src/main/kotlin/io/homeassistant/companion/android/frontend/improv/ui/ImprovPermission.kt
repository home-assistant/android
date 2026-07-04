package io.homeassistant.companion.android.frontend.improv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth

/**
 * Body of the bluetooth-and-location permission rationale shown before the system multi-permission
 * dialog when the frontend triggers the Improv Wi-Fi onboarding flow.
 *
 * Renders as plain content without its own bottom-sheet frame — embed inside an
 * [io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet] (or the
 * legacy `BottomSheetDialogFragment` host).
 *
 * @param needsBluetooth Whether the Bluetooth bullet should be displayed.
 * @param needsLocation Whether the Location bullet should be displayed.
 * @param onContinue Invoked when the user taps Continue. The caller must show the system
 *   multi-permission dialog and persist the displayed-count increment.
 * @param onSkip Invoked when the user taps Skip / dismisses without granting permission.
 */
@Composable
fun ImprovPermission(
    needsBluetooth: Boolean,
    needsLocation: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(HADimens.SPACE4),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header()
        Text(
            text = stringResource(commonR.string.improv_permission_text),
            style = HATextStyle.Body,
        )
        if (needsBluetooth) {
            PermissionBullet(
                icon = CommunityMaterial.Icon.cmd_bluetooth,
                text = stringResource(commonR.string.improv_permission_bluetooth),
            )
        }
        if (needsLocation) {
            PermissionBullet(
                icon = CommunityMaterial.Icon3.cmd_map_marker,
                text = stringResource(commonR.string.improv_permission_location),
            )
        }

        Spacer(Modifier.height(HADimens.SPACE8))

        HAAccentButton(
            text = stringResource(commonR.string.continue_connect),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        HAPlainButton(
            text = stringResource(commonR.string.skip),
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.Header(modifier: Modifier = Modifier) {
    Spacer(modifier = Modifier.height(HADimens.SPACE4))
    Image(
        asset = CommunityMaterial.Icon3.cmd_radar,
        colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorOnPrimaryNormal),
        contentDescription = null,
        modifier = Modifier
            .size(48.dp),
    )
    Text(
        text = stringResource(commonR.string.improv_permission_title),
        style = HATextStyle.HeadlineMedium,
    )
}

@Composable
private fun PermissionBullet(icon: IIcon, text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.widthIn(max = MaxButtonWidth).fillMaxWidth(),
    ) {
        Image(
            asset = icon,
            colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorTextPrimary),
            contentDescription = null,
        )
        Text(
            text = text,
            style = HATextStyle.Body,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .padding(start = HADimens.SPACE4)
                .fillMaxWidth(),
        )
    }
}

@Preview
@PreviewLightDark
@Composable
private fun ImprovPermissionPreview() {
    HAThemeForPreview {
        ImprovPermission(
            needsBluetooth = true,
            needsLocation = true,
            onContinue = {},
            onSkip = {},
        )
    }
}

@Preview
@Composable
private fun ImprovPermissionBluetoothOnlyPreview() {
    HAThemeForPreview {
        ImprovPermission(
            needsBluetooth = true,
            needsLocation = false,
            onContinue = {},
            onSkip = {},
        )
    }
}
