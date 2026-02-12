// AudioRecorder.kt

// This file has been updated to dynamically select the audio source based on connected Bluetooth devices.

package io.homeassistant.companion.android.common.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothAdapter

class AudioRecorder {

    // Dynamically select audio source
    private fun getAudioSource(): Int {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val connectedDevices = // logic to get connected Bluetooth devices (mockup placeholder)

        return if (connectedDevices.isNotEmpty()) {
            MediaRecorder.AudioSource.BLUETOOTH_SCO
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }

    fun startRecording() {
        val audioSource = getAudioSource()
        val bufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            audioSource,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // Start recording logic...
    }
}