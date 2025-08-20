package io.homeassistant.companion.android.frontend

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.onboarding.R
import timber.log.Timber

/**
 * Dummy screen that represent the HAFrontend. Used for implementing and testing
 * navigation it would the equivalent of [WebViewActivity].
 */
@Composable
fun FrontendScreen(url: String?) {
    Text("Connected to Home Assistant Frontend\n $url")
    NotificationBottomSheet(
        permissionAlreadyAsked = true,
        {
            Timber.e("Result from permission accepted ? $it")
            // TODO store the value so that we don't bother the user with it anymore
            // on Minimal settingViewModel.updateWebsocketSetting(if (enabled) WebsocketSetting.ALWAYS else WebsocketSetting.NEVER,)
        },
    )
}

@SuppressLint("InlinedApi")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun NotificationBottomSheet(
    permissionAlreadyAsked: Boolean, // When our DB doesn't contains the value we should ask for permission
    onNotificationPermissionResult: (Boolean) -> Unit,
) {
    // on Old version of Android this return Granted by default so we should still ask for permission

    val notificationPermission = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS,
        onPermissionResult = onNotificationPermissionResult,
    )

    if (!permissionAlreadyAsked || notificationPermission.status != PermissionStatus.Granted) {
        val bottomSheetState =
            rememberStandardBottomSheetState(skipHiddenState = false, initialValue = SheetValue.Expanded)

        ModalBottomSheet(
            sheetState = bottomSheetState,
            onDismissRequest = {},
            shape = RoundedCornerShape(topStart = HARadius.M, topEnd = HARadius.M),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = HASpacing.XL)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HASpacing.XL),
            ) {
                Text(
                    text = stringResource(R.string.allow_notification_title),
                    style = HATextStyle.Headline,
                )
                Text(
                    text = stringResource(R.string.allow_notification_content),
                    style = HATextStyle.Body,
                )
                HAAccentButton(
                    text = stringResource(R.string.allow_notification_button),
                    onClick = {
                        notificationPermission.launchPermissionRequest()
                    },
                )
                HAFilledButton(
                    text = stringResource(R.string.allow_notification_not_allow),
                    onClick = { onNotificationPermissionResult(false) },
                    modifier = Modifier.padding(bottom = HASpacing.XL),
                )
            }
        }
    }
}
