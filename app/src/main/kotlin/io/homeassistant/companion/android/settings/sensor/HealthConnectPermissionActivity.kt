package io.homeassistant.companion.android.settings.sensor

import android.content.Intent
import androidx.core.net.toUri
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR

class HealthConnectPermissionActivity : BaseActivity() {
    override fun onResume() {
        super.onResume()
        if (
            intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            (
                intent.action == "android.intent.action.VIEW_PERMISSION_USAGE" &&
                    intent.hasCategory("android.intent.category.HEALTH_PERMISSIONS")
                )
        ) {
            Intent(Intent.ACTION_VIEW, resources.getString(commonR.string.privacy_url).toUri()).also {
                startActivity(it)
            }
        }
        finish()
    }
}
