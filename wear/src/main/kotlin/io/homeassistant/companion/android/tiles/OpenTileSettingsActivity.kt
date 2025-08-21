package io.homeassistant.companion.android.tiles

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl
import io.homeassistant.companion.android.home.HomeActivity
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OpenTileSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepositoryImpl

    companion object {

        const val TILE_ID_KEY = "tile_id"
        private const val TILE_ID_CLOCKWORK = "com.google.android.clockwork.EXTRA_PROVIDER_CONFIG_TILE_ID"
        const val CONFIG_CAMERA_TILE = "ConfigCameraTile"
        const val CONFIG_SHORTCUT_TILE = "ConfigShortcutsTile"
        const val CONFIG_TEMPLATE_TILE = "ConfigTemplateTile"
        const val CONFIG_THERMOSTAT_TILE = "ConfigThermostatTile"

        fun newInstance(context: ComponentActivity, action: String, tileId: Int): Intent {
            return Intent(
                context,
                OpenTileSettingsActivity::class.java,
            )
                .setAction(action)
                .putExtra(TILE_ID_KEY, tileId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        val tileId = when {
            extras?.containsKey(TILE_ID_CLOCKWORK) == true -> extras.getInt(TILE_ID_CLOCKWORK)
            extras?.containsKey(TILE_ID_KEY) == true -> extras.getInt(TILE_ID_KEY)
            else -> null
        }
        tileId?.takeIf { it != 0 }?.let {
            val settingsIntent = when (intent.action) {
                CONFIG_CAMERA_TILE ->
                    HomeActivity.getCameraTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                CONFIG_SHORTCUT_TILE -> {
                    lifecycleScope.launch {
                        wearPrefsRepository.getTileShortcutsAndSaveTileId(tileId)
                    }
                    HomeActivity.getShortcutsTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                }
                CONFIG_TEMPLATE_TILE -> {
                    lifecycleScope.launch {
                        wearPrefsRepository.getTemplateTileAndSaveTileId(tileId)
                    }
                    HomeActivity.getTemplateTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                }
                CONFIG_THERMOSTAT_TILE ->
                    HomeActivity.getThermostatTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                else -> null
            }
            settingsIntent?.let { startActivity(settingsIntent) }
        }
        finish()
    }
}
