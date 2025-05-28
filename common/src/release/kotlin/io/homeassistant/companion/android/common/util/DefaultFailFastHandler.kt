package io.homeassistant.companion.android.common.util

import timber.log.Timber

object DefaultFailFastHandler : FailFastHandler {
    override fun handleException(exception: Exception) {
        Timber.e(
            exception,
            """
            Fail fast caught an exception. The exception is ignored in production to avoid a crash but it should be handled
            if you see this please open an issue on github https://github.com/home-assistant/android/issues/new/choose"""
                .trimIndent()
        )
        // no-op
    }
}
