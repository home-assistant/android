package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer

class SettingsWearMainView : AppCompatActivity(), DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "SettingsWearMainView"

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearMainView::class.java)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // No op
    }
}
