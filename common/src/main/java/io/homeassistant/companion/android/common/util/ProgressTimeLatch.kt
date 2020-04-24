package io.homeassistant.companion.android.common.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class ProgressTimeLatch(
    private val delayMs: Long = 750,
    private val minShowTime: Long = 500,
    defaultValue: Boolean,
    private val refreshingToggle: ((Boolean) -> Unit)
) {
    private val handler = Handler(Looper.getMainLooper())
    private var showTime = 0L

    private val delayedShow = Runnable(::show)
    private val delayedHide = Runnable(::hideAndReset)

    var refreshing: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                handler.removeCallbacks(delayedShow)
                handler.removeCallbacks(delayedHide)

                if (value) {
                    handler.postDelayed(delayedShow, delayMs)
                } else if (showTime >= 0) {
                    // We're already showing, lets check if we need to delay the hide
                    val showTime = SystemClock.uptimeMillis() - showTime
                    if (showTime < minShowTime) {
                        handler.postDelayed(delayedHide, minShowTime - showTime)
                    } else {
                        // We've been showing longer than the min, so hide and clean up
                        hideAndReset()
                    }
                } else {
                    // We're not currently show so just hide and clean up
                    hideAndReset()
                }
            }
        }

    init {
        refreshing = defaultValue
    }

    private fun show() {
        refreshingToggle(true)
        showTime = SystemClock.uptimeMillis()
    }

    private fun hideAndReset() {
        refreshingToggle(false)
        showTime = 0
    }

    fun destroy() {
        handler.removeCallbacks(delayedHide)
        handler.removeCallbacks(delayedShow)
    }

}