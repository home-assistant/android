package io.homeassistant.companion.android

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
