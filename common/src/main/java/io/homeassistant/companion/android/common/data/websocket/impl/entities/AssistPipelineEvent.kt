package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class AssistPipelineEvent(
    val type: String,
    val data: AssistPipelineEventData?
)

object AssistPipelineEventType {
    const val RUN_START = "run-start"
    const val RUN_END = "run-end"
    const val STT_START = "stt-start"
    const val STT_END = "stt-end"
    const val INTENT_START = "intent-start"
    const val INTENT_END = "intent-end"
    const val TTS_START = "tts-start"
    const val TTS_END = "tts-end"
    const val ERROR = "error"
}

interface AssistPipelineEventData

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineRunStart(
    val pipeline: String,
    val language: String,
    val runnerData: Map<String, Any?>
) : AssistPipelineEventData

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineSttEnd(
    val sttOutput: Map<String, Any?>
) : AssistPipelineEventData

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineIntentStart(
    val engine: String,
    val language: String,
    val intentInput: String
) : AssistPipelineEventData

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineIntentEnd(
    val intentOutput: ConversationResponse
) : AssistPipelineEventData

@JsonIgnoreProperties
data class AssistPipelineTtsEnd(
    val ttsOutput: TtsOutputResponse
) : AssistPipelineEventData

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineError(
    val code: String?,
    val message: String?
) : AssistPipelineEventData
