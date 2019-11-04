package io.homeassistant.companion.android

import android.app.Application
import io.homeassistant.companion.android.api.Session

class HomeAssistantApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Session.init(this)
    }

}