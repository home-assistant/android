package io.homeassistant.companion.android.common.util

import androidx.fragment.app.FragmentActivity
import timber.log.Timber

/**
 * Executes fragment transactions only if the FragmentManager state hasn't been saved.
 *
 * This prevents [IllegalStateException] when attempting fragment transactions after
 * [FragmentActivity.onSaveInstanceState] has been called (e.g., when the user backgrounds the app).
 * This is particularly useful when transactions are triggered from coroutines or callbacks
 * that may execute asynchronously after the activity's state has been saved.
 *
 * @param block The fragment transaction to execute if the state hasn't been saved.
 */
inline fun FragmentActivity.runFragmentTransactionIfStateSafe(block: () -> Unit) {
    if (!supportFragmentManager.isStateSaved) {
        block()
    } else {
        Timber.d("Skipping fragment transaction - state already saved")
    }
}
