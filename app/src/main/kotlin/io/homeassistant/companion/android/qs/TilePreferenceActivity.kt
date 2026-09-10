package io.homeassistant.companion.android.qs

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.qs.tileSlots
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class TilePreferenceActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var tileDao: TileDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var tileId = "-1"
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            intent.extras?.let { extras ->
                BundleCompat.getParcelable(
                    extras,
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName::class.java,
                )?.let { component ->
                    try {
                        val tileClass = Class.forName(component.className)
                        tileSlots.firstOrNull { it.serviceClass == tileClass }?.id?.value?.let {
                            Timber.d("Tile ID for long press action: $it")
                            tileId = it
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Couldn't get tile ID for component $component")
                    }
                }
            }
        }

        lifecycleScope.launch {
            val tileData = tileDao.get(tileId)

            val intent = if (!serverManager.isRegistered()) {
                Intent(this@TilePreferenceActivity, LaunchActivity::class.java)
            } else if (tileData?.isSetup == true) {
                this@TilePreferenceActivity.intentLaunchWithNavigateTo(
                    target = FrontendTarget.EntityMoreInfo(tileData.entityId),
                    serverId = tileData.serverId,
                )
            } else {
                SettingsActivity.newInstance(this@TilePreferenceActivity, SettingsActivity.Deeplink.QSTile(tileId))
            }

            withContext(Dispatchers.Main) {
                startActivity(intent)
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0) // Disable activity start/stop animation
            }
        }
    }
}
