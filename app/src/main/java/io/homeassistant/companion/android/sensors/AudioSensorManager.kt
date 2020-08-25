package io.homeassistant.companion.android.sensors

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

class AudioSensorManager : SensorManager {
    companion object {
        private const val TAG = "AudioSensor"

        private val audioSensor = SensorManager.BasicSensor(
            "audio_sensor",
            "sensor",
            "Audio Sensor"
        )
    }

    override val name: String
        get() = "Audio Sensor"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(audioSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        updateAudioSensor(context)
    }

    private fun updateAudioSensor(context: Context) {
        if (!isEnabled(context, audioSensor.id))
            return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioMode = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "normal"
            AudioManager.MODE_RINGTONE -> "ringing"
            AudioManager.MODE_IN_CALL -> "in_call"
            AudioManager.MODE_IN_COMMUNICATION -> "in_communication"
            else -> "unknown"
        }

        val ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_SILENT -> "silent"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "unknown"
        }

        val isMicMuted = audioManager.isMicrophoneMute

        val isMusicActive = audioManager.isMusicActive

        val isSpeakerOn = audioManager.isSpeakerphoneOn
        var isHeadphones = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (deviceInfo in audioDevices) {
                if (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || deviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET)
                    isHeadphones = true
            }
        } else {
            // Use deprecated method as getDevices is API 23 and up only and we support API 21
            isHeadphones = audioManager.isWiredHeadsetOn
        }

        val volumeLevelAlarm = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val volumeLevelCall = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val volumeLevelMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeLevelRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)

        val icon = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "mdi:volume-high"
            AudioManager.RINGER_MODE_SILENT -> "mdi:volume-off"
            AudioManager.RINGER_MODE_VIBRATE -> "mdi:vibrate"
            else -> "mdi:volume-low"
        }

        onSensorUpdated(context,
            audioSensor,
            ringerMode,
            icon,
            mapOf(
                "audio_mode" to audioMode,
                "is_headphones" to isHeadphones,
                "is_mic_muted" to isMicMuted,
                "is_music_active" to isMusicActive,
                "is_speakerphone_on" to isSpeakerOn,
                "volume_level_alarm" to volumeLevelAlarm,
                "volume_level_call" to volumeLevelCall,
                "volume_level_music" to volumeLevelMusic,
                "volume_level_ring" to volumeLevelRing
            )
        )
    }
}
