package io.homeassistant.companion.android.lock

interface LockPresenter {
    fun isLockEnabled(): Boolean

    fun onViewReady()
}
