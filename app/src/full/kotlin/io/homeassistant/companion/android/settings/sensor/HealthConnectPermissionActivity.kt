package io.homeassistant.companion.android.settings.sensor

import android.content.Intent
import android.net.Uri
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
            Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(commonR.string.privacy_url))).also {
                startActivity(it)
            }
        }
        finish()
    }
}
