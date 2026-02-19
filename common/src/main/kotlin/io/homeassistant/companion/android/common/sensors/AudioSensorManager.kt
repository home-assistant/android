package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager.BasicSensor
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import java.util.concurrent.ConcurrentHashMap

class AudioSensorManager : SensorManager {
    private val sensorIdsWhereAttributesAreAlreadyForced: MutableSet<String> = ConcurrentHashMap.newKeySet()

    companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val ATTRIBUTE_MIN = "min"
        private const val ATTRIBUTE_MAX = "max"

        val audioSensor = SensorManager.BasicSensor(
            "audio_sensor",
            "sensor",
            commonR.string.sensor_name_ringer_mode,
            commonR.string.sensor_description_audio_sensor,
            "mdi:volume-high",
            deviceClass = "enum",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        private val audioState = SensorManager.BasicSensor(
            "audio_mode",
            "sensor",
            commonR.string.sensor_name_audio_mode,
            commonR.string.sensor_description_audio_mode,
            "mdi:volume-high",
            deviceClass = "enum",
        )
        private val headphoneState = SensorManager.BasicSensor(
            "headphone_state",
            "binary_sensor",
            commonR.string.sensor_name_headphone,
            commonR.string.sensor_description_headphone,
            "mdi:headphones",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val micMuted = SensorManager.BasicSensor(
            "mic_muted",
            "binary_sensor",
            commonR.string.sensor_name_mic_muted,
            commonR.string.sensor_description_mic_muted,
            "mdi:microphone-off",
            updateType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SensorManager.BasicSensor.UpdateType.INTENT
                } else {
                    SensorManager.BasicSensor.UpdateType.WORKER
                },
        )
        private val musicActive = SensorManager.BasicSensor(
            "music_active",
            "binary_sensor",
            commonR.string.sensor_name_music_active,
            commonR.string.sensor_description_music_active,
            "mdi:music",
        )
        val speakerphoneState = SensorManager.BasicSensor(
            "speakerphone_state",
            "binary_sensor",
            commonR.string.sensor_name_speakerphone,
            commonR.string.sensor_description_speakerphone,
            "mdi:volume-high",
            updateType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SensorManager.BasicSensor.UpdateType.INTENT
                } else {
                    SensorManager.BasicSensor.UpdateType.WORKER
                },
        )
        val volAlarm = SensorManager.BasicSensor(
            "volume_alarm",
            "sensor",
            commonR.string.sensor_name_volume_alarm,
            commonR.string.sensor_description_volume_alarm,
            "mdi:alarm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volCall = SensorManager.BasicSensor(
            "volume_call",
            "sensor",
            commonR.string.sensor_name_volume_call,
            commonR.string.sensor_description_volume_call,
            "mdi:phone",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volMusic = SensorManager.BasicSensor(
            "volume_music",
            "sensor",
            commonR.string.sensor_name_volume_music,
            commonR.string.sensor_description_volume_music,
            "mdi:music",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volRing = SensorManager.BasicSensor(
            "volume_ring",
            "sensor",
            commonR.string.sensor_name_volume_ring,
            commonR.string.sensor_description_volume_ring,
            "mdi:phone-ring",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volNotification = SensorManager.BasicSensor(
            "volume_notification",
            "sensor",
            commonR.string.sensor_name_volume_notification,
            commonR.string.sensor_description_volume_notification,
            "mdi:bell-ring",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volSystem = SensorManager.BasicSensor(
            "volume_system",
            "sensor",
            commonR.string.sensor_name_volume_system,
            commonR.string.sensor_description_volume_system,
            "mdi:cellphone-sound",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volAccessibility = SensorManager.BasicSensor(
            "volume_accessibility",
            "sensor",
            commonR.string.sensor_name_volume_accessibility,
            commonR.string.sensor_description_volume_accessibility,
            "mdi:human",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val volDTMF = SensorManager.BasicSensor(
            "volume_dtmf",
            "sensor",
            commonR.string.sensor_name_volume_dtmf,
            commonR.string.sensor_description_volume_dtmf,
            "mdi:volume-high",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#audio-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_audio

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        val allSupportedSensors = listOf(
            audioSensor, audioState, headphoneState, micMuted, speakerphoneState,
            musicActive, volAlarm, volCall, volMusic, volRing, volNotification, volSystem,
            volDTMF,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            allSupportedSensors.plus(volAccessibility)
        } else {
            allSupportedSensors
        }
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        val audioManager = context.getSystemService<AudioManager>()!!
        updateAudioSensor(context, audioManager)
        updateAudioState(context, audioManager)
        updateHeadphoneState(context, audioManager)
        updateMicMuted(context, audioManager)
        updateMusicActive(context, audioManager)
        updateSpeakerphoneState(context, audioManager)
        updateVolumeAlarm(context, audioManager)
        updateVolumeCall(context, audioManager)
        updateVolumeMusic(context, audioManager)
        updateVolumeRing(context, audioManager)
        updateVolumeNotification(context, audioManager)
        updateVolumeSystem(context, audioManager)
        updateVolumeDTMF(context, audioManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updateVolumeAccessibility(context, audioManager)
        }
    }

    private suspend fun updateAudioSensor(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, audioSensor)) {
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
            context,
            audioSensor,
            ringerMode,
            icon,
            mapOf(
                "options" to listOf("normal", "silent", "vibrate"),
            ),
        )
    }

    private suspend fun updateAudioState(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, audioState)) {
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
            else -> "mdi:volume-low"
        }

        onSensorUpdated(
            context,
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
                ),
            ),
        )
    }

    private suspend fun updateHeadphoneState(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, headphoneState)) {
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
            context,
            headphoneState,
            isHeadphones,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateMicMuted(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, micMuted)) {
            return
        }

        val isMicMuted = audioManager.isMicrophoneMute

        val icon = if (!isMicMuted) "mdi:microphone" else "mdi:microphone-off"

        onSensorUpdated(
            context,
            micMuted,
            isMicMuted,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateMusicActive(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, musicActive)) {
            return
        }

        val isMusicActive = audioManager.isMusicActive

        val icon = if (isMusicActive) "mdi:music" else "mdi:music-off"

        onSensorUpdated(
            context,
            musicActive,
            isMusicActive,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateSpeakerphoneState(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, speakerphoneState)) {
            return
        }

        // Use deprecated function as we can't perfectly map communication device to speakerphone
        @Suppress("DEPRECATION")
        val isSpeakerOn = audioManager.isSpeakerphoneOn

        val icon = if (isSpeakerOn) "mdi:volume-high" else "mdi:volume-off"

        onSensorUpdated(
            context,
            speakerphoneState,
            isSpeakerOn,
            icon,
            mapOf(),
        )
    }

    private suspend fun updateVolumeAlarm(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volAlarm, AudioManager.STREAM_ALARM)
    }

    private suspend fun updateVolumeCall(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volCall, AudioManager.STREAM_VOICE_CALL)
    }

    private suspend fun updateVolumeMusic(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volMusic, AudioManager.STREAM_MUSIC)
    }

    private suspend fun updateVolumeRing(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volRing, AudioManager.STREAM_RING)
    }

    private suspend fun updateVolumeNotification(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volNotification, AudioManager.STREAM_NOTIFICATION)
    }

    private suspend fun updateVolumeSystem(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volSystem, AudioManager.STREAM_SYSTEM)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun updateVolumeAccessibility(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volAccessibility, AudioManager.STREAM_ACCESSIBILITY)
    }

    private suspend fun updateVolumeDTMF(context: Context, audioManager: AudioManager) {
        updateVolumeSensor(context, audioManager, volDTMF, AudioManager.STREAM_DTMF)
    }

    private suspend fun updateVolumeSensor(
        context: Context,
        audioManager: AudioManager,
        sensor: BasicSensor,
        streamType: Int,
    ) {
        if (!isEnabled(context, sensor)) {
            return
        }

        val current = audioManager.getStreamVolume(streamType)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(streamType)
        } else {
            0
        }
        val max = audioManager.getStreamMaxVolume(streamType)

        val force = !areAttributesOfSensorAlreadyForcedAtRuntimeOrMatching(context, sensor, min, max)

        onSensorUpdated(
            context,
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
     * values, or have already been force-updated during this app runtime.
     *
     * Usually, min and max do not change while the app is running. The only meaningful
     * moments when they can be out of date:
     * 1. An older companion app ran before, without pushing min/max.
     * 2. The operating system changed.
     *
     * Therefore, the actual database check is performed at most once per sensor per app
     * runtime. On subsequent calls for the same sensor, this returns `true` immediately
     * without a database query.
     */
    private suspend fun areAttributesOfSensorAlreadyForcedAtRuntimeOrMatching(
        context: Context,
        sensor: BasicSensor,
        currentMin: Int,
        currentMax: Int,
    ): Boolean {
        if (sensor.id in sensorIdsWhereAttributesAreAlreadyForced) {
            return true
        }

        val sensorsWithAttributes = sensorDao(context)
            .getFull(sensor.id)
            .toSensorsWithAttributes()

        if (sensorsWithAttributes.isNotEmpty() && sensorsWithAttributes.all { sensorWithAttributes ->
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
            sensorIdsWhereAttributesAreAlreadyForced += sensor.id
            return true
        }

        return false
    }
}
