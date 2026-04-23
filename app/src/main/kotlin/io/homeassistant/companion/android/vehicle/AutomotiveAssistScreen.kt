package io.homeassistant.companion.android.vehicle

import android.app.Application
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.compose.runtime.snapshotFlow
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.homeassistant.companion.android.assist.AssistAudioStrategyFactory
import io.homeassistant.companion.android.assist.AutomotiveAssistViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.assist.AssistViewModelBase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AudioUrlPlayer
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/** A CarApp Screen that displays the automotive Assist UI.
 * It uses a simplified interface suitable for vehicle use.
 */
@RequiresApi
class AutomotiveAssistScreen @AssistedInject constructor(
    @Assisted private val carContext: CarContext,
    @Assisted private val serverManager: ServerManager,
    @Assisted private val serverId: Int,
    @Assisted private val audioStrategyFactory: AssistAudioStrategyFactory,
    @Assisted private val audioUrlPlayer: AudioUrlPlayer,
    @Assisted private val application: Application,
    @Assisted private val viewModel: AutomotiveAssistViewModel,
    @Assisted private val scope: CoroutineScope,
) : Screen(carContext) {

    init {
        scope.launch {
            viewModel.conversation.collect {
                invalidate()
            }
        }
        scope.launch {
            viewModel.processingState.collect {
                invalidate()
            }
        }
        scope.launch {
            viewModel.isAudioPlaying.collect {
                invalidate()
            }
        }
        scope.launch {
            snapshotFlow { viewModel.inputMode }.collect {
                invalidate()
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            carContext: CarContext,
            serverManager: ServerManager,
            serverId: Int,
            audioStrategyFactory: AssistAudioStrategyFactory,
            audioUrlPlayer: AudioUrlPlayer,
            application: Application,
            viewModel: AutomotiveAssistViewModel,
            scope: CoroutineScope,
        ): AutomotiveAssistScreen
    }

    override fun onGetTemplate(): Template {
        Timber.d("onGetTemplate called")
        val conversation = viewModel.conversation.value
        val isPlayingAudio = viewModel.isAudioPlaying.value
        val isProcessing = viewModel.isProcessing
        val inputMode = viewModel.getInput()

        val icon = when {
            inputMode == AssistViewModelBase.AssistInputMode.VOICE_ACTIVE -> {
                CommunityMaterial.Icon3.cmd_microphone
            }
            isPlayingAudio -> {
                CommunityMaterial.Icon3.cmd_speaker
            }
            isProcessing -> {
                CommunityMaterial.Icon3.cmd_sync
            }
            else -> {
                CommunityMaterial.Icon3.cmd_microphone_outline
            }
        }

        val header = carContext.getHeaderBuilder(commonR.string.assist_how_can_i_assist).apply {
            addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconicsDrawable(
                                carContext,
                                icon,
                            ).apply {
                                sizeDp = 32
                            }.toAndroidIconCompat(),
                        ).setTint(CarColor.DEFAULT).build(),
                    )
                    .setOnClickListener {
                        Timber.d("Assist button clicked")
                        viewModel.onMicrophoneInput(proactive = true, clearConversation = true)
                    }
                    .build(),
            )
        }.build()

        val itemListBuilder = ItemList.Builder()
        conversation.forEach { msg ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(CarText.create(if (msg.isInput) "You" else "Assistant"))
                    .addText(CarText.create(msg.message))
                    .build(),
            )
        }

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
