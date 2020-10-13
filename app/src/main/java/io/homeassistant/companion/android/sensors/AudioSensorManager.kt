package io.homeassistant.companion.android.sensors

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import io.homeassistant.companion.android.R

class AudioSensorManager : SensorManager {
    companion object {
        private const val TAG = "AudioSensor"

        private val audioSensor = SensorManager.BasicSensor(
            "audio_sensor",
            "sensor",
            R.string.sensor_name_ringer_mode,
            R.string.sensor_description_audio_sensor
        )
        private val audioState = SensorManager.BasicSensor(
            "audio_mode",
            "sensor",
            R.string.sensor_name_audio_mode,
            R.string.sensor_description_audio_mode
        )
        private val headphoneState = SensorManager.BasicSensor(
            "headphone_state",
            "binary_sensor",
            R.string.sensor_name_headphone,
            R.string.sensor_description_headphone
        )
        private val micMuted = SensorManager.BasicSensor(
            "mic_muted",
            "binary_sensor",
            R.string.sensor_name_mic_muted,
            R.string.sensor_description_mic_muted
        )
        private val musicActive = SensorManager.BasicSensor(
            "music_active",
            "binary_sensor",
            R.string.sensor_name_music_active,
            R.string.sensor_description_music_active
        )
        private val speakerphoneState = SensorManager.BasicSensor(
            "speakerphone_state",
            "binary_sensor",
            R.string.sensor_name_speakerphone,
            R.string.sensor_description_speakerphone
        )
        private val volAlarm = SensorManager.BasicSensor(
            "volume_alarm",
            "sensor",
            R.string.sensor_name_volume_alarm,
            R.string.sensor_description_volume_alarm
        )
        private val volCall = SensorManager.BasicSensor(
            "volume_call",
            "sensor",
            R.string.sensor_name_volume_call,
            R.string.sensor_description_volume_call
        )
        private val volMusic = SensorManager.BasicSensor(
            "volume_music",
            "sensor",
            R.string.sensor_name_volume_music,
            R.string.sensor_description_volume_music
        )
        private val volRing = SensorManager.BasicSensor(
            "volume_ring",
            "sensor",
            R.string.sensor_name_volume_ring,
            R.string.sensor_description_volume_ring
        )
    }

    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = R.string.sensor_name_audio

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(audioSensor, audioState, headphoneState, micMuted, speakerphoneState, musicActive, volAlarm, volCall, volMusic, volRing)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

        onSensorUpdated(context,
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
            else -> "unknown"
        }

        val icon = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "mdi:volume-high"
            AudioManager.MODE_RINGTONE -> "mdi:phone-ring"
            AudioManager.MODE_IN_CALL -> "mdi:phone"
            AudioManager.MODE_IN_COMMUNICATION -> "mdi:message-video"
            else -> "mdi:volume-low"
        }

        onSensorUpdated(context,
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
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (deviceInfo in audioDevices) {
                if (deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || deviceInfo.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || deviceInfo.type == AudioDeviceInfo.TYPE_USB_HEADSET)
                    isHeadphones = true
            }
        } else {
            // Use deprecated method as getDevices is API 23 and up only and we support API 21
            isHeadphones = audioManager.isWiredHeadsetOn
        }

        val icon = if (isHeadphones) "mdi:headphones" else "mdi:headphones-off"

        onSensorUpdated(context,
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

        onSensorUpdated(context,
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

        onSensorUpdated(context,
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

        onSensorUpdated(context,
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

        val icon = "mdi:alarm"

        onSensorUpdated(context,
            volAlarm,
            volumeLevelAlarm,
            icon,
            mapOf()
        )
    }

    private fun updateVolumeCall(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volCall.id))
            return

        val volumeLevelCall = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)

        val icon = "mdi:phone"

        onSensorUpdated(context,
            volCall,
            volumeLevelCall,
            icon,
            mapOf()
        )
    }

    private fun updateVolumeMusic(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volMusic.id))
            return

        val volumeLevelMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val icon = "mdi:music"

        onSensorUpdated(context,
            volMusic,
            volumeLevelMusic,
            icon,
            mapOf()
        )
    }

    private fun updateVolumeRing(context: Context, audioManager: AudioManager) {
        if (!isEnabled(context, volRing.id))
            return

        val volumeLevelRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)

        val icon = "mdi:phone-ring"

        onSensorUpdated(context,
            volRing,
            volumeLevelRing,
            icon,
            mapOf()
        )
    }
}
