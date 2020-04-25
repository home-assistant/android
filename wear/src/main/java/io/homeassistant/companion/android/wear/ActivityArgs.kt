package io.homeassistant.companion.android.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle

interface ActivityArgs {

    interface Factory<T> {
        fun fromBundle(bundle: Bundle): T
        fun fromIntent(intent: Intent): T
    }

    fun saveInstance(bundle: Bundle)
    fun startActivity(activity: Activity)

}

fun <T> ActivityArgs.Factory<T>.buildArgs(intent: Intent, bundle: Bundle?): T {
    return if (bundle != null) fromBundle(bundle) else fromIntent(intent)
}