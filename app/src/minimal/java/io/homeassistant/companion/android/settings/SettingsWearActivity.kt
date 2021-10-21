package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo

class SettingsWearActivity : AppCompatActivity(), CapabilityClient.OnCapabilityChangedListener {

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        // No op
    }

    companion object {
        private const val TAG = "SettingsWearAct"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearActivity::class.java)
        }
    }
}
