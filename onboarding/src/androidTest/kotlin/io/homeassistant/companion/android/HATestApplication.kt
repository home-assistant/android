package io.homeassistant.companion.android

import android.app.Application
import dagger.hilt.android.testing.CustomTestApplication
import timber.log.Timber

@CustomTestApplication(BaseHATestApplication::class)
interface HATestApplication

/**
 * Base class for the custom test application to use in instrumentation tests.
 */
open class BaseHATestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
