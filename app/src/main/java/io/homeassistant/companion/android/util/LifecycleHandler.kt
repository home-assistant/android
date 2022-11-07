package io.homeassistant.companion.android.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

object LifecycleHandler : Application.ActivityLifecycleCallbacks {
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        // Not implemented
    }

    override fun onActivityStarted(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        activityReferences++
    }

    override fun onActivityResumed(activity: Activity) {
        // Not implemented
    }

    override fun onActivityPaused(activity: Activity) {
        // Not implemented
    }

    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        activityReferences--
    }

    override fun onActivitySaveInstanceState(activity: Activity, bunle: Bundle) {
        // Not implemented
    }

    override fun onActivityDestroyed(activity: Activity) {
        // Not implemented
    }

    fun isAppInBackground(): Boolean {
        // No activities left and activity is not changing configuration (ex. change the orientation)
        return activityReferences == 0 && !isActivityChangingConfigurations
    }
}
