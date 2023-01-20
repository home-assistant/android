package io.homeassistant.companion.android.conversation.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.conversation.ConversationViewModel
import io.homeassistant.companion.android.home.views.TimeText
import io.homeassistant.companion.android.theme.WearAppTheme

@Composable
fun ConversationResultView(
    conversationViewModel: ConversationViewModel
) {

    val scrollState = rememberScalingLazyListState()

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scrollState.isScrollInProgress)
                    PositionIndicator(scalingLazyListState = scrollState)
            },
            timeText = { TimeText(visible = !scrollState.isScrollInProgress) }
        ) {
            ScalingLazyColumn(
                state = scrollState,
                horizontalAlignment = Alignment.Start
            ) {
                item {
                    Column {
                        Spacer(Modifier.padding(24.dp))
                        SpeechBubble(
                            text = conversationViewModel.speechResult.ifEmpty {
                                if (conversationViewModel.supportsConversation)
                                    stringResource(R.string.no_results)
                                else
                                    stringResource(R.string.no_conversation_support)
                            },
                            false
                        )
                        Spacer(Modifier.padding(8.dp))
                    }
                }
                if (conversationViewModel.conversationResult.isNotEmpty())
                    item {
                        if (conversationViewModel.isHapticEnabled.value) {
                            val haptic = LocalHapticFeedback.current
                            LaunchedEffect(key1 = "haptic") {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                            }
                        }
                        SpeechBubble(
                            text = conversationViewModel.conversationResult,
                            true
                        )
                    }
            }
        }
    }
}

@Composable
fun SpeechBubble(text: String, isResponse: Boolean) {
    Row(
        horizontalArrangement = if (isResponse) Arrangement.Start else Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isResponse) 0.dp else 24.dp,
                end = if (isResponse) 24.dp else 0.dp
            )
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isResponse)
                        colorResource(R.color.colorAccent)
                    else
                        colorResource(R.color.colorSpeechText),
                    AbsoluteRoundedCornerShape(
                        topLeftPercent = 40,
                        topRightPercent = 40,
                        bottomLeftPercent = if (isResponse) 0 else 40,
                        bottomRightPercent = if (isResponse) 40 else 0
                    )
                )
                .padding(4.dp)
        ) {
            Text(
                text = text,
                color = if (isResponse)
                    Color.White
                else
                    Color.Black,
                modifier = Modifier
                    .padding(4.dp)
            )
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND)
@Composable
fun PreviewSpeechBubble() {
    ScalingLazyColumn(horizontalAlignment = Alignment.Start) {
        item {
            SpeechBubble(text = "Speech", isResponse = false)
        }
        item {
            SpeechBubble(text = "Response", isResponse = true)
        }
    }
}
