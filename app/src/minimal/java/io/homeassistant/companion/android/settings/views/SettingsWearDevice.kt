package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient

class SettingsWearDevice : AppCompatActivity(), DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "SettingsWearDevice"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearDevice::class.java)
        }
    }
}
