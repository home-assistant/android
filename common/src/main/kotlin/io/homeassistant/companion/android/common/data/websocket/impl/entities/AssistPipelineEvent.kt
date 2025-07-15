package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

data class AssistPipelineEvent(val type: String, val data: AssistPipelineEventData?)

object AssistPipelineEventType {
    const val RUN_START = "run-start"
    const val RUN_END = "run-end"
    const val STT_START = "stt-start"
    const val STT_END = "stt-end"
    const val INTENT_START = "intent-start"
    const val INTENT_PROGRESS = "intent-progress"
    const val INTENT_END = "intent-end"
    const val TTS_START = "tts-start"
    const val TTS_END = "tts-end"
    const val ERROR = "error"
}

interface AssistPipelineEventData

@Serializable
data class AssistPipelineRunStart(
    val pipeline: String,
    val language: String,
    // TODO replace the map https://github.com/home-assistant/android/issues/5341
    @Serializable(with = MapAnySerializer::class)
    val runnerData: Map<String, @Polymorphic Any?>,
) : AssistPipelineEventData

@Serializable
data class AssistPipelineSttEnd(
    // TODO Replace the map https://github.com/home-assistant/android/issues/5341
    @Serializable(with = MapAnySerializer::class)
    val sttOutput: Map<String, @Polymorphic Any?>,
) : AssistPipelineEventData

@Serializable
data class AssistPipelineIntentStart(val engine: String, val language: String, val intentInput: String) :
    AssistPipelineEventData

@Serializable
data class AssistPipelineIntentProgress(val chatLogDelta: AssistChatLogDelta?) : AssistPipelineEventData

@Serializable
data class AssistChatLogDelta(val content: String? = null)

@Serializable
data class AssistPipelineIntentEnd(val intentOutput: ConversationResponse) : AssistPipelineEventData

@Serializable
data class AssistPipelineTtsEnd(val ttsOutput: TtsOutputResponse) : AssistPipelineEventData

@Serializable
data class AssistPipelineError(val code: String? = null, val message: String? = null) : AssistPipelineEventData
