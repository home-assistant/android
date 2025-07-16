package io.homeassistant.companion.android.settings.url.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.safeBottomPaddingValues

@Composable
fun ExternalUrlView(
    canUseCloud: Boolean,
    useCloud: Boolean,
    externalUrl: String?,
    onUseCloudToggle: (Boolean) -> Unit,
    onExternalUrlSaved: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .padding(safeBottomPaddingValues(applyHorizontal = false))
            .padding(vertical = 16.dp),
    ) {
        if (canUseCloud) {
            ExternalUrlCloudView(
                useCloud = useCloud,
                onUseCloudToggle = onUseCloudToggle,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (!canUseCloud || !useCloud) {
            ExternalUrlInputView(
                url = externalUrl,
                focusRequester = focusRequester,
                onSaveUrl = onExternalUrlSaved,
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!canUseCloud) focusRequester.requestFocus()
    }
}

@Composable
fun ExternalUrlCloudView(useCloud: Boolean, onUseCloudToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onUseCloudToggle(!useCloud) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(commonR.string.input_cloud),
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(
            checked = useCloud,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = colorResource(commonR.color.colorSwitchUncheckedThumb),
            ),
        )
    }
}

@Preview
@Composable
fun PreviewExternalUrlViewCloudOn() {
    ExternalUrlView(
        canUseCloud = true,
        useCloud = true,
        externalUrl = "https://home.example.com:8123/",
        onUseCloudToggle = {},
        onExternalUrlSaved = {},
    )
}

@Preview
@Composable
fun PreviewExternalUrlViewCloudOff() {
    ExternalUrlView(
        canUseCloud = true,
        useCloud = false,
        externalUrl = "https://home.example.com:8123/",
        onUseCloudToggle = {},
        onExternalUrlSaved = {},
    )
}

@Preview
@Composable
fun PreviewExternalUrlViewCloudNone() {
    ExternalUrlView(
        canUseCloud = false,
        useCloud = false,
        externalUrl = "https://home.example.com:8123/",
        onUseCloudToggle = {},
        onExternalUrlSaved = {},
    )
}
