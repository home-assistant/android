package com.example.homeassistantwearos

import android.os.Bundle
import android.support.wearable.activity.WearableActivity

class Home : WearableActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Enables Always-on
        setAmbientEnabled()
    }
}
