package io.homeassistant.companion.android.sensors

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class AudioSensorManager : SensorManager {
    companion object {
        private const val TAG = "AudioSensor"

        val audioSensor = SensorManager.BasicSensor(
            "audio_sensor",
            "sensor",
            commonR.string.sensor_name_ringer_mode,
            commonR.string.sensor_description_audio_sensor,
            "mdi:volume-high",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        private val audioState = SensorManager.BasicSensor(
            "audio_mode",
            "sensor",
            commonR.string.sensor_name_audio_mode,
            commonR.string.sensor_description_audio_mode,
            "mdi:volume-high"
        )
        private val headphoneState = SensorManager.BasicSensor(
            "headphone_state",
            "binary_sensor",
            commonR.string.sensor_name_headphone,
            commonR.string.sensor_description_headphone,
            "mdi:headphones",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val micMuted = SensorManager.BasicSensor(
            "mic_muted",
            "binary_sensor",
            commonR.string.sensor_name_mic_muted,
            commonR.string.sensor_description_mic_muted,
            "mdi:microphone-off",
            updateType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) SensorManager.BasicSensor.UpdateType.INTENT
            else SensorManager.BasicSensor.UpdateType.WORKER
        )
        private val musicActive = SensorManager.BasicSensor(
            "music_active",
            "binary_sensor",
            commonR.string.sensor_name_music_active,
            commonR.string.sensor_description_music_active,
            "mdi:music"
        )
        val speakerphoneState = SensorManager.BasicSensor(
            "speakerphone_state",
            "binary_sensor",
            commonR.string.sensor_name_speakerphone,
            commonR.string.sensor_description_speakerphone,
            "mdi:volume-high",
            updateType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) SensorManager.BasicSensor.UpdateType.INTENT
            else SensorManager.BasicSensor.UpdateType.WORKER
        )
        private val volAlarm = SensorManager.BasicSensor(
            "volume_alarm",
            "sensor",
            commonR.string.sensor_name_volume_alarm,
            commonR.string.sensor_description_volume_alarm,
            "mdi:alarm",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volCall = SensorManager.BasicSensor(
            "volume_call",
            "sensor",
            commonR.string.sensor_name_volume_call,
            commonR.string.sensor_description_volume_call,
            "mdi:phone",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volMusic = SensorManager.BasicSensor(
            "volume_music",
            "sensor",
            commonR.string.sensor_name_volume_music,
            commonR.string.sensor_description_volume_music,
            "mdi:music",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volRing = SensorManager.BasicSensor(
            "volume_ring",
            "sensor",
            commonR.string.sensor_name_volume_ring,
            commonR.string.sensor_description_volume_ring,
            "mdi:phone-ring",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volNotification = SensorManager.BasicSensor(
            "volume_notification",
            "sensor",
            commonR.string.sensor_name_volume_notification,
            commonR.string.sensor_description_volume_notification,
            "mdi:bell-ring",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volSystem = SensorManager.BasicSensor(
            "volume_system",
            "sensor",
            commonR.string.sensor_name_volume_system,
            commonR.string.sensor_description_volume_system,
            "mdi:cellphone-sound",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volAccessibility = SensorManager.BasicSensor(
            "volume_accessibility",
            "sensor",
            commonR.string.sensor_name_volume_accessibility,
            commonR.string.sensor_description_volume_accessibility,
            "mdi:human",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val volDTMF = SensorManager.BasicSensor(
            "volume_dtmf",
            "sensor",
            commonR.string.sensor_name_volume_dtmf,
            commonR.string.sensor_description_volume_dtmf,
            "mdi:volume-high",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#audio-sensors"
    }

    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = commonR.string.sensor_name_audio

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        val allSupportedSensors = listOf(
            audioSensor, audioState, headphoneState, micMuted, speakerphoneState,
            musicActive, volAlarm, volCall, volMusic, volRing, volNotification, volSystem,
            volDTMF
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            allSupportedSensors.plus(volAccessibility)
        else
            allSupportedSensors
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            updateVolumeAccessibility(context, audioManager)
    }

    private fun updateAudioSensor(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, audioSensor.id))
            return

        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "unknown"
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
            mapOf()
        )
    }

    private fun updateAudioState(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, audioState.id))
            return
        val audioMode = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "normal"
            AudioManager.MODE_RINGTONE -> "ringing"
            AudioManager.MODE_IN_CALL -> "in_call"
            AudioManager.MODE_IN_COMMUNICATION -> "in_communication"
            AudioManager.MODE_CALL_SCREENING -> "call_screening"
            else -> "unknown"
        }

        val icon = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "mdi:volume-high"
            AudioManager.MODE_RINGTONE -> "mdi:phone-ring"
            AudioManager.MODE_IN_CALL -> "mdi:phone"
            AudioManager.MODE_IN_COMMUNICATION -> "mdi:message-video"
            AudioManager.MODE_CALL_SCREENING -> "mdi:text-to-speech"
            else -> "mdi:volume-low"
        }

        onSensorUpdated(
            context,
            audioState,
            audioMode,
            icon,
            mapOf()
        )
    }

    private fun updateHeadphoneState(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, headphoneState.id))
            return

        var isHeadphones = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (deviceInfo in audioDevices) {
                if (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || deviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET)
                    isHeadphones = true
            }
        } else {
            // Use deprecated method as getDevices is API 23 and up only and we support API 21
            isHeadphones = audioManager.isWiredHeadsetOn
        }

        val icon = if (isHeadphones) "mdi:headphones" else "mdi:headphones-off"

        onSensorUpdated(
            context,
            headphoneState,
            isHeadphones,
            icon,
            mapOf()
        )
    }

    private fun updateMicMuted(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, micMuted.id))
            return

        val isMicMuted = audioManager.isMicrophoneMute

        val icon = if (!isMicMuted) "mdi:microphone" else "mdi:microphone-off"

        onSensorUpdated(
            context,
            micMuted,
            isMicMuted,
            icon,
            mapOf()
        )
    }

    private fun updateMusicActive(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, musicActive.id))
            return

        val isMusicActive = audioManager.isMusicActive

        val icon = if (isMusicActive) "mdi:music" else "mdi:music-off"

        onSensorUpdated(
            context,
            musicActive,
            isMusicActive,
            icon,
            mapOf()
        )
    }

    private fun updateSpeakerphoneState(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, speakerphoneState.id))
            return

        val isSpeakerOn = audioManager.isSpeakerphoneOn

        val icon = if (isSpeakerOn) "mdi:volume-high" else "mdi:volume-off"

        onSensorUpdated(
            context,
            speakerphoneState,
            isSpeakerOn,
            icon,
            mapOf()
        )
    }

    private fun updateVolumeAlarm(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volAlarm.id))
            return
        val volumeLevelAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        onSensorUpdated(
            context,
            volAlarm,
            volumeLevelAlarm,
            volAlarm.statelessIcon,
            mapOf()
        )
    }

    private fun updateVolumeCall(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volCall.id))
            return

        val volumeLevelCall = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)

        onSensorUpdated(
            context,
            volCall,
            volumeLevelCall,
            volCall.statelessIcon,
            mapOf()
        )
    }

    private fun updateVolumeMusic(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volMusic.id))
            return

        val volumeLevelMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        onSensorUpdated(
            context,
            volMusic,
            volumeLevelMusic,
            volMusic.statelessIcon,
            mapOf()
        )
    }

    private fun updateVolumeRing(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volRing.id))
            return

        val volumeLevelRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)

        onSensorUpdated(
            context,
            volRing,
            volumeLevelRing,
            volRing.statelessIcon,
            mapOf()
        )
    }

    private fun updateVolumeNotification(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volNotification.id))
            return

        val volumeLevelNotification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

        onSensorUpdated(
            context,
            volNotification,
            volumeLevelNotification,
            volNotification.statelessIcon,
            mapOf()
        )
    }

    private fun updateVolumeSystem(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volSystem.id))
            return

        val volumeLevelSystem = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)

        onSensorUpdated(
            context,
            volSystem,
            volumeLevelSystem,
            volSystem.statelessIcon,
            mapOf()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateVolumeAccessibility(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volAccessibility.id))
            return

        val volumeLevelAccessibility = audioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY)

        onSensorUpdated(
            context,
            volAccessibility,
            volumeLevelAccessibility,
            volAccessibility.statelessIcon,
            mapOf()
        )
    }

    private fun updateVolumeDTMF(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volDTMF.id))
            return

        val volumeLevelDTMF = audioManager.getStreamVolume(AudioManager.STREAM_DTMF)

        onSensorUpdated(
            context,
            volDTMF,
            volumeLevelDTMF,
            volDTMF.statelessIcon,
            mapOf()
        )
    }
}
