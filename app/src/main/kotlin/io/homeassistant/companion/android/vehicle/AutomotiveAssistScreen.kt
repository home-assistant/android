package io.homeassistant.companion.android.vehicle

import android.app.Application
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.CarText
import androidx.car.app.model.Template
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.assist.AutomotiveAssistViewModel
import io.homeassistant.companion.android.assist.AssistAudioStrategyFactory
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.common.R as commonR
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject
import android.app.Application

/** A CarApp Screen that displays the automotive Assist UI.
 * It uses a simplified interface suitable for vehicle use.
 */
@RequiresApi
class AutomotiveAssistScreen(
    private val carContext: CarContext,
    private val serverManager: ServerManager,
    private val serverId: Int,
    private val audioStrategyFactory: AssistAudioStrategyFactory,
    private val audioUrlPlayer: AudioUrlPlayer,
    private val application: Application
) : Screen() {

    private val viewModel: AutomotiveAssistViewModel by AutomotiveAssistViewModel.Factory(
        carContext,
        serverManager,
        serverId,
        audioStrategyFactory,
        audioUrlPlayer,
        application
    )


    @Composable
    override fun onBuildRenderModel(): Template {
        val conversation = viewModel.conversation
        val lastMessage = conversation.lastOrNull()
        val messageText = lastMessage?.message ?: commonR.string.assist_how_can_i_assist

        return MessageTemplate.Builder(CarText.create(messageText))
            .addAction(
                Action.Builder()
                .setTitle(CarText.create(commonR.string.assist_retry))
                .setOnClickListener { viewModel.onMicrophoneInput() }
                .build()
            )
            .build()
    }
}
