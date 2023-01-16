package io.homeassistant.companion.android.conversation.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.conversation.ConversationViewModel
import io.homeassistant.companion.android.home.views.TimeText
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ThemeLazyColumn

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
            ThemeLazyColumn(
                state = scrollState
            ) {
                item {
                    Spacer(modifier = Modifier.padding(40.dp))
                    SpeechBubble(
                        text = conversationViewModel.speechResult.ifEmpty {
                            if (conversationViewModel.supportsConversation)
                                stringResource(R.string.no_results)
                            else
                                stringResource(R.string.no_conversation_support)
                        },
                        false
                    )
                }
                if (conversationViewModel.conversationResult.isNotEmpty())
                    item {
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
    Box(
        modifier = Modifier
            .background(
                if (isResponse)
                    colorResource(R.color.colorAccent)
                else
                    Color.White,
                AbsoluteRoundedCornerShape(
                    topLeftPercent = 40,
                    topRightPercent = 40,
                    bottomLeftPercent = if (isResponse) 40 else 0,
                    bottomRightPercent = if (isResponse) 0 else 40
                )
            )
            .padding(4.dp)
    ) {
        Text(
            text = text,
            color = if (isResponse)
                Color.White
            else
                Color.Black
        )
    }
}

@Preview
@Composable
fun PreviewSpeechBubble() {
    SpeechBubble(text = "Testing", isResponse = true)
}
