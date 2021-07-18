package io.homeassistant.companion.android

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
    }
}