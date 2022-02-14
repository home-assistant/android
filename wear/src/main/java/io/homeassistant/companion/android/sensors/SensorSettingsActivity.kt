package io.homeassistant.companion.android.sensors

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R

@AndroidEntryPoint
class SensorSettingsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                replace(R.id.content, SensorsSettingsFragment())
            }
        }
    }
}
