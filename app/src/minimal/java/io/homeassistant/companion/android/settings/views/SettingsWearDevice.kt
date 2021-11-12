package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer

class SettingsWearDevice : AppCompatActivity(), DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "SettingsWearDevice"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearDevice::class.java)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // No op
    }
}
