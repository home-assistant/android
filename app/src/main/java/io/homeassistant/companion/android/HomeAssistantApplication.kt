package io.homeassistant.companion.android

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen

class HomeAssistantApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AndroidThreeTen.init(this)
    }

}