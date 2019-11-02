package io.homeassistant.android

import android.app.Application
import io.homeassistant.android.api.Session

class HomeAssistantApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Session.init(this)
    }

}