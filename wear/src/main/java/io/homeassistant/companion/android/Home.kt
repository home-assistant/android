package io.homeassistant.companion.android

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import io.homeassistant.companion.android.databinding.ActivityHomeBinding

class Home : FragmentActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AmbientModeSupport.attach(this)
        binding.batteryLevel.text = getBatteryInfoPhone()
    }

    private fun getBatteryInfoPhone(): String {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = registerReceiver(null, iFilter)
//        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        return "$level%"
    }
}
