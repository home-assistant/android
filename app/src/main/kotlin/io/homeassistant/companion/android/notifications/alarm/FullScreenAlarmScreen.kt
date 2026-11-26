package io.homeassistant.companion.android.notifications.alarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.ShieldAlert
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.util.compose.HAPreviews

private val AlarmIconSize = 120.dp
private const val ICON_PULSE_SCALE = 1.15f
private const val ICON_PULSE_DURATION_MS = 600

/**
 * Full-screen alarm content: the notification title and message, its actions, and buttons to open the
 * notification target or dismiss the alarm.
 */
@Composable
internal fun FullScreenAlarmScreen(
    uiState: FullScreenAlarmUiState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onActionClick: (FullScreenAlarmAction) -> Unit,
    onAllowFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalHAColorScheme.current.colorSurfaceDefault)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(HADimens.SPACE4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PulsingAlarmIcon(icon = uiState.icon)
        AlarmText(title = uiState.title, message = uiState.message)
        if (!uiState.isFullScreenIntentAllowed) {
            FullScreenPermissionBanner(onAllowFullScreen = onAllowFullScreen)
        }
        Spacer(modifier = Modifier.weight(1f))
        AlarmButtons(
            actions = uiState.actions,
            canOpen = uiState.canOpen,
            onDismiss = onDismiss,
            onOpen = onOpen,
            onActionClick = onActionClick,
        )
    }
}

@Composable
private fun PulsingAlarmIcon(icon: MdiIcon) {
    val transition = rememberInfiniteTransition(label = "alarmIconPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = ICON_PULSE_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ICON_PULSE_DURATION_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alarmIconScale",
    )
    Image(
        imageVector = icon.rememberImageVector(),
        contentDescription = null,
        colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorFillDangerLoudResting),
        modifier = Modifier
            .size(AlarmIconSize)
            .scale(scale),
    )
}

@Composable
private fun AlarmText(title: String, message: String) {
    if (title.isNotBlank()) {
        Text(
            text = title,
            style = HATextStyle.Headline,
            modifier = Modifier.widthIn(max = MaxButtonWidth),
        )
    }
    if (message.isNotBlank()) {
        Text(
            text = message,
            style = HATextStyle.Body,
            modifier = Modifier.widthIn(max = MaxButtonWidth),
        )
    }
}

@Composable
private fun FullScreenPermissionBanner(onAllowFullScreen: () -> Unit) {
    HABanner(modifier = Modifier.widthIn(max = MaxButtonWidth)) {
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
            Text(
                text = stringResource(commonR.string.full_screen_alarm_permission_missing),
                style = HATextStyle.Body.copy(textAlign = TextAlign.Start),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                HAAccentButton(text = stringResource(commonR.string.allow), onClick = onAllowFullScreen)
            }
        }
    }
}

@Composable
private fun ColumnScope.AlarmButtons(
    actions: List<FullScreenAlarmAction>,
    canOpen: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onActionClick: (FullScreenAlarmAction) -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .align(Alignment.CenterHorizontally)
    actions.forEach { action ->
        HAFilledButton(
            text = action.title,
            onClick = { onActionClick(action) },
            variant = ButtonVariant.NEUTRAL,
            size = ButtonSize.LARGE,
            modifier = buttonModifier,
        )
    }
    if (canOpen) {
        HAFilledButton(
            text = stringResource(commonR.string.full_screen_alarm_open),
            onClick = onOpen,
            size = ButtonSize.LARGE,
            modifier = buttonModifier,
        )
    }
    HAAccentButton(
        text = stringResource(commonR.string.full_screen_alarm_dismiss),
        onClick = onDismiss,
        variant = ButtonVariant.DANGER,
        size = ButtonSize.LARGE,
        modifier = buttonModifier,
    )
}

@HAPreviews
@Composable
private fun FullScreenAlarmScreenPreview() {
    HAThemeForPreview {
        FullScreenAlarmScreen(
            uiState = FullScreenAlarmUiState(
                icon = Mdi.ShieldAlert,
                title = "Intrusion detected",
                message = "Motion detected in the living room while the alarm is armed away",
                canOpen = true,
                actions = listOf(
                    FullScreenAlarmAction(index = 0, title = "Silence siren"),
                    FullScreenAlarmAction(index = 1, title = "Call security"),
                ),
                isFullScreenIntentAllowed = false,
            ),
            onDismiss = {},
            onOpen = {},
            onActionClick = {},
            onAllowFullScreen = {},
        )
    }
}
