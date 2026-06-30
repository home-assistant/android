package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    private val sensorIdsWithMatchingAttributes: MutableSet<String> = ConcurrentHashMap.newKeySet()

    companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val ATTRIBUTE_MIN = "min"
        private const val ATTRIBUTE_MAX = "max"

        @ProvidesSensor
        val audioSensor = SensorManager.BasicSensor(
            "audio_sensor",
            "sensor",
            commonR.string.sensor_name_ringer_mode,
            commonR.string.sensor_description_audio_sensor,
            "mdi:volume-high",
            deviceClass = "enum",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        internal val audioState = SensorManager.BasicSensor(
            "audio_mode",
            "sensor",
            commonR.string.sensor_name_audio_mode,
            commonR.string.sensor_description_audio_mode,
            "mdi:volume-high",
            deviceClass = "enum",
        )

        @ProvidesSensor
        internal val headphoneState = SensorManager.BasicSensor(
            "headphone_state",
            "binary_sensor",
            commonR.string.sensor_name_headphone,
            commonR.string.sensor_description_headphone,
            "mdi:headphones",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val micMuted = SensorManager.BasicSensor(
            "mic_muted",
            "binary_sensor",
            commonR.string.sensor_name_mic_muted,
            commonR.string.sensor_description_mic_muted,
            "mdi:microphone-off",
            updateType =
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.P)) {
                SensorManager.BasicSensor.UpdateType.INTENT
            } else {
                SensorManager.BasicSensor.UpdateType.WORKER
            },
        )

        @ProvidesSensor
        internal val musicActive = SensorManager.BasicSensor(
            "music_active",
            "binary_sensor",
            commonR.string.sensor_name_music_active,
            commonR.string.sensor_description_music_active,
            "mdi:music",
        )

        @ProvidesSensor
        val speakerphoneState = SensorManager.BasicSensor(
            "speakerphone_state",
            "binary_sensor",
            commonR.string.sensor_name_speakerphone,
            commonR.string.sensor_description_speakerphone,
            "mdi:volume-high",
            updateType =
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                SensorManager.BasicSensor.UpdateType.INTENT
            } else {
                SensorManager.BasicSensor.UpdateType.WORKER
            },
        )

        @ProvidesSensor
        val volAlarm = SensorManager.BasicSensor(
            "volume_alarm",
            "sensor",
            commonR.string.sensor_name_volume_alarm,
            commonR.string.sensor_description_volume_alarm,
            "mdi:alarm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volCall = SensorManager.BasicSensor(
            "volume_call",
            "sensor",
            commonR.string.sensor_name_volume_call,
            commonR.string.sensor_description_volume_call,
            "mdi:phone",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volMusic = SensorManager.BasicSensor(
            "volume_music",
            "sensor",
            commonR.string.sensor_name_volume_music,
            commonR.string.sensor_description_volume_music,
            "mdi:music",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volRing = SensorManager.BasicSensor(
            "volume_ring",
            "sensor",
            commonR.string.sensor_name_volume_ring,
            commonR.string.sensor_description_volume_ring,
            "mdi:phone-ring",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volNotification = SensorManager.BasicSensor(
            "volume_notification",
            "sensor",
            commonR.string.sensor_name_volume_notification,
            commonR.string.sensor_description_volume_notification,
            "mdi:bell-ring",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volSystem = SensorManager.BasicSensor(
            "volume_system",
            "sensor",
            commonR.string.sensor_name_volume_system,
            commonR.string.sensor_description_volume_system,
            "mdi:cellphone-sound",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volAccessibility = SensorManager.BasicSensor(
            "volume_accessibility",
            "sensor",
            commonR.string.sensor_name_volume_accessibility,
            commonR.string.sensor_description_volume_accessibility,
            "mdi:human",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volDTMF = SensorManager.BasicSensor(
            "volume_dtmf",
            "sensor",
            commonR.string.sensor_name_volume_dtmf,
            commonR.string.sensor_description_volume_dtmf,
            "mdi:volume-high",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val volAssistant = SensorManager.BasicSensor(
            "volume_assistant",
            "sensor",
            commonR.string.sensor_name_volume_assistant,
            commonR.string.sensor_description_volume_assistant,
            "mdi:assistant",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#audio-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_audio

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return buildList {
            addAll(
                listOf(
                    audioSensor, audioState, headphoneState, micMuted, speakerphoneState,
                    musicActive, volAlarm, volCall, volMusic, volRing, volNotification, volSystem,
                    volDTMF,
                ),
            )
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                add(volAccessibility)
            }
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.CINNAMON_BUN)) {
                add(volAssistant)
            }
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        val audioManager = applicationContext.getSystemService<AudioManager>()!!
        updateAudioSensor(audioManager)
        updateAudioState(audioManager)
        updateHeadphoneState(audioManager)
        updateMicMuted(audioManager)
        updateMusicActive(audioManager)
        updateSpeakerphoneState(audioManager)
        updateVolumeAlarm(audioManager)
        updateVolumeCall(audioManager)
        updateVolumeMusic(audioManager)
        updateVolumeRing(audioManager)
        updateVolumeNotification(audioManager)
        updateVolumeSystem(audioManager)
        updateVolumeDTMF(audioManager)
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            updateVolumeAccessibility(audioManager)
        }
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.CINNAMON_BUN)) {
            updateVolumeAssistant(audioManager)
        }
    }

    private suspend fun updateAudioSensor(audioManager: AudioManager) {
        if (!isEnabled(audioSensor)) {
            return
        }

        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> STATE_UNKNOWN
        }

        val icon = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "mdi:volume-high"
            AudioManager.RINGER_MODE_SILENT -> "mdi:volume-off"
            AudioManager.RINGER_MODE_VIBRATE -> "mdi:vibrate"
            else -> "mdi:volume-low"
        }

        onSensorUpdated(
            audioSensor,
            ringerMode,
            icon,
            mapOf(
                "options" to listOf("normal", "silent", "vibrate"),
            ),
        )
    }

    private suspend fun updateAudioState(audioManager: AudioManager) {
        if (!isEnabled(audioState)) {
            return
        }
        val audioMode = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "normal"
            AudioManager.MODE_RINGTONE -> "ringing"
            AudioManager.MODE_IN_CALL -> "in_call"
            AudioManager.MODE_IN_COMMUNICATION -> "in_communication"
            AudioManager.MODE_CALL_SCREENING -> "call_screening"
            AudioManager.MODE_CALL_REDIRECT -> "call_redirect"
            AudioManager.MODE_COMMUNICATION_REDIRECT -> "communication_redirect"
            AudioManager.MODE_ASSISTANT_CONVERSATION -> "assistant_conversation"
            else -> STATE_UNKNOWN
        }

        val icon = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "mdi:volume-high"
            AudioManager.MODE_RINGTONE -> "mdi:phone-ring"
            AudioManager.MODE_IN_CALL -> "mdi:phone"
            AudioManager.MODE_IN_COMMUNICATION -> "mdi:message-video"
            AudioManager.MODE_CALL_SCREENING -> "mdi:microphone-message"
            AudioManager.MODE_CALL_REDIRECT -> "mdi:phone"
            AudioManager.MODE_COMMUNICATION_REDIRECT -> "mdi:message-video"
            AudioManager.MODE_ASSISTANT_CONVERSATION -> "mdi:assistant"
            else -> "mdi:volume-low"
        }

        onSensorUpdated(
            audioState,
            audioMode,
            icon,
            mapOf(
                "options" to listOf(
                    "normal",
                    "ringing",
                    "in_call",
                    "in_communication",
                    "call_screening",
                    "call_redirect",
                    "communication_redirect",
                    "assistant_conversation",
                ),
            ),
        )
    }

    private suspend fun updateHeadphoneState(audioManager: AudioManager) {
        if (!isEnabled(headphoneState)) {
            return
        }

        var isHeadphones = false
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (deviceInfo in audioDevices) {
            if (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                deviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET
            ) {
                isHeadphones = true
            }
        }

        val icon = if (isHeadphones) "mdi:headphones" else "mdi:headphones-off"

        onSensorUpdated(
            headphoneState,
            isHeadphones,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateMicMuted(audioManager: AudioManager) {
        if (!isEnabled(micMuted)) {
            return
        }

        val isMicMuted = audioManager.isMicrophoneMute

        val icon = if (!isMicMuted) "mdi:microphone" else "mdi:microphone-off"

        onSensorUpdated(
            micMuted,
            isMicMuted,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateMusicActive(audioManager: AudioManager) {
        if (!isEnabled(musicActive)) {
            return
        }

        val isMusicActive = audioManager.isMusicActive

        val icon = if (isMusicActive) "mdi:music" else "mdi:music-off"

        onSensorUpdated(
            musicActive,
            isMusicActive,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateSpeakerphoneState(audioManager: AudioManager) {
        if (!isEnabled(speakerphoneState)) {
            return
        }

        // Use deprecated function as we can't perfectly map communication device to speakerphone
        @Suppress("DEPRECATION")
        val isSpeakerOn = audioManager.isSpeakerphoneOn

        val icon = if (isSpeakerOn) "mdi:volume-high" else "mdi:volume-off"

        onSensorUpdated(
            speakerphoneState,
            isSpeakerOn,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateVolumeAlarm(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volAlarm, AudioManager.STREAM_ALARM)
    }

    private suspend fun updateVolumeCall(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volCall, AudioManager.STREAM_VOICE_CALL)
    }

    private suspend fun updateVolumeMusic(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volMusic, AudioManager.STREAM_MUSIC)
    }

    private suspend fun updateVolumeRing(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volRing, AudioManager.STREAM_RING)
    }

    private suspend fun updateVolumeNotification(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volNotification, AudioManager.STREAM_NOTIFICATION)
    }

    private suspend fun updateVolumeSystem(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volSystem, AudioManager.STREAM_SYSTEM)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun updateVolumeAccessibility(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volAccessibility, AudioManager.STREAM_ACCESSIBILITY)
    }

    private suspend fun updateVolumeDTMF(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volDTMF, AudioManager.STREAM_DTMF)
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private suspend fun updateVolumeAssistant(audioManager: AudioManager) {
        updateVolumeSensor(audioManager, volAssistant, AudioManager.STREAM_ASSISTANT)
    }

    private suspend fun updateVolumeSensor(
        audioManager: AudioManager,
        sensor: SensorManager.BasicSensor,
        streamType: Int,
    ) {
        if (!isEnabled(sensor)) {
            return
        }

        val current = audioManager.getStreamVolume(streamType)
        val min = if (SdkVersion.isAtLeast(Build.VERSION_CODES.P)) {
            audioManager.getStreamMinVolume(streamType)
        } else {
            0
        }
        val max = audioManager.getStreamMaxVolume(streamType)

        val force = !areAttributesOfSensorAlreadyForcedAtRuntimeOrMatching(sensor, min, max)

        onSensorUpdated(
            sensor,
            current,
            sensor.statelessIcon,
            mapOf(
                ATTRIBUTE_MIN to min,
                ATTRIBUTE_MAX to max,
            ),
            force,
        )
    }

    /**
     * Checks whether the stored min/max attributes for a sensor already match the current
     * values, using a runtime cache to avoid repeated database queries.
     *
     * Usually, min and max do not change while the app is running. The only meaningful
     * moments when they can be out of date:
     * 1. An older companion app ran before, without pushing min/max.
     * 2. The operating system changed.
     *
     * Once the stored attributes are found to match for a given sensor, the result is
     * cached and subsequent calls return `true` immediately without a database query.
     * If the attributes do not match, the database is queried again on each call until
     * they do.
     */
    private suspend fun areAttributesOfSensorAlreadyForcedAtRuntimeOrMatching(
        sensor: SensorManager.BasicSensor,
        currentMin: Int,
        currentMax: Int,
    ): Boolean {
        if (sensor.id in sensorIdsWithMatchingAttributes) {
            return true
        }

        val sensorsWithAttributes = sensorRepository
            .getFull(sensor.id)
            .toSensorsWithAttributes()

        if (sensorsWithAttributes.isNotEmpty() &&
            sensorsWithAttributes.all { sensorWithAttributes ->
                val attributes = sensorWithAttributes.attributes
                val storedMin = attributes
                    .firstOrNull { it.name == ATTRIBUTE_MIN }
                    ?.value
                    ?.toIntOrNull()
                val storedMax = attributes
                    .firstOrNull { it.name == ATTRIBUTE_MAX }
                    ?.value
                    ?.toIntOrNull()
                storedMin == currentMin && storedMax == currentMax
            }
        ) {
            sensorIdsWithMatchingAttributes += sensor.id
            return true
        }

        return false
    }
}
