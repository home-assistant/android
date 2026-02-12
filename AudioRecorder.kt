// Other existing imports
import android.media.AudioManager;
import android.telecom.Call;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

// Function to detect connected Bluetooth devices
private AudioDevice getAudioSource() {
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getType() == BluetoothDevice.DEVICE_TYPE_AUDIO) {
                // return audio source associated with the Bluetooth device
                return AudioDevice.BLUETOOTH;
            }
        }
    }
    // Fallback to default audio source if no Bluetooth devices are connected
    return AudioDevice.DEFAULT;
}