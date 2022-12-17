package io.homeassistant.companion.android.conversation.views

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                    Text(
                        text = conversationViewModel.speechResult.ifEmpty {
                            if (conversationViewModel.supportsConversation)
                                stringResource(R.string.no_results)
                            else
                                stringResource(R.string.no_conversation_support)
                        },
                        modifier = Modifier.padding(40.dp)
                    )
                }
                if (conversationViewModel.conversationResult.isNotEmpty())
                    item {
                        Text(
                            text = conversationViewModel.conversationResult,
                            modifier = Modifier.padding(top = 8.dp, start = 32.dp)
                        )
                    }
            }
        }
    }
}
