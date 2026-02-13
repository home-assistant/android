package io.homeassistant.companion.android.frontend.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import kotlinx.coroutines.launch

/**
 * Displays a bottom sheet prompting the user to grant notification permission.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun NotificationPermissionPrompt(onPermissionResult: (Boolean) -> Unit) {
    val bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val coroutineScope = rememberCoroutineScope()

    // Track whether the bottom sheet has been dismissed to completely remove it from composition.
    // This is necessary because Material 3's ModalBottomSheet creates a Dialog window that,
    // even when hidden, can block touch events to underlying views like the WebView.
    // By removing the composable entirely when dismissed (checking !isClosed), we ensure
    // the Dialog window is destroyed and the WebView remains fully interactive.
    var isClosed by remember { mutableStateOf(false) }

    fun closeSheet() {
        coroutineScope.launch {
            bottomSheetState.hide()
            isClosed = true
        }
    }

    // By default on lower API the bottom sheet won't be displayed
    val notificationPermission = rememberPermissionState(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        previewPermissionStatus = PermissionStatus.Denied(true),
        onPermissionResult = {
            onPermissionResult(it)
            closeSheet()
        },
    )

    if (!isClosed) {
        HAModalBottomSheet(
            bottomSheetState = bottomSheetState,
            onDismissRequest = {
                closeSheet()
            },
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = HADimens.SPACE6)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
            ) {
                Text(
                    text = stringResource(commonR.string.notification_permission_dialog_title),
                    style = HATextStyle.HeadlineMedium,
                )
                Text(
                    text = stringResource(commonR.string.notification_permission_dialog_content),
                    style = HATextStyle.Body,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
                ) {
                    HAAccentButton(
                        text = stringResource(commonR.string.notification_permission_dialog_allow),
                        onClick = {
                            notificationPermission.launchPermissionRequest()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    HAPlainButton(
                        text = stringResource(commonR.string.notification_permission_dialog_deny),
                        onClick = {
                            onPermissionResult(false)
                            closeSheet()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = HADimens.SPACE6),
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview
@Composable
private fun NotificationPermissionPromptPreview() {
    HAThemeForPreview {
        NotificationPermissionPrompt(onPermissionResult = {})
    }
}
