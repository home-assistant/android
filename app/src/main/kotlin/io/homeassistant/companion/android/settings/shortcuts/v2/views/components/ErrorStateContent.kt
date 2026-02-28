package io.homeassistant.companion.android.settings.shortcuts.v2.views.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.settings.views.EmptyState

@Composable
internal fun ErrorStateContent(onRetry: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = CommunityMaterial.Icon.cmd_alert,
            title = stringResource(R.string.shortcuts_error_title),
            subtitle = stringResource(R.string.shortcuts_error_subtitle),
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@Preview(name = "Error State Content")
@Composable
private fun ErrorStateContentPreview() {
    HAThemeForPreview {
        ErrorStateContent(onRetry = {})
    }
}
