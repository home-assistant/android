import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioRecorder(private val context: Context) {

    var audioSource: Int = MediaRecorder.AudioSource.MIC

    init {
        setupAudioSource()
    }

    private fun setupAudioSource() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Check if Bluetooth headset is connected
        if (audioManager.isBluetoothScoAvailableOffCall()) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    audioSource = MediaRecorder.AudioSource.BLUETOOTH_SCO
                    break
                }
            }
        }

        // Fall back to VOICE_COMMUNICATION if Bluetooth is not available
        if (audioSource == MediaRecorder.AudioSource.MIC) {
            audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }
    }

    fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioFormat.CHANNEL_IN_MONO, 
            AudioFormat.ENCODING_PCM_16BIT, 
            audioSource
        )

        val audioRecord = AudioRecord(
            audioSource,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )

        audioRecord.startRecording()
        // Handle recording logic here
    }

    fun stopRecording() {
        // Handle stopping logic here
    }
}