package io.homeassistant.companion.android.wear.action

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.ui.ActivityArgs

class ActionActivityArgs(val action: WearAction?) :
    ActivityArgs {

    companion object : ActivityArgs.Factory<ActionActivityArgs> {
        private const val KEY_ACTION = "ActionActivityArgs.ACTION"

        override fun fromBundle(bundle: Bundle): ActionActivityArgs {
            return ActionActivityArgs(bundle.getParcelable(KEY_ACTION))
        }

        override fun fromIntent(intent: Intent): ActionActivityArgs {
            return ActionActivityArgs(intent.getParcelableExtra(KEY_ACTION))
        }
    }

    val isUpdate: Boolean get() = action != null

    override fun saveInstance(bundle: Bundle) {
        bundle.putParcelable(KEY_ACTION, action)
    }

    override fun startActivity(activity: Activity) {
        val intent = Intent(activity, ActionActivity::class.java)
            .putExtra(KEY_ACTION, action)
        activity.startActivity(intent)
    }

}