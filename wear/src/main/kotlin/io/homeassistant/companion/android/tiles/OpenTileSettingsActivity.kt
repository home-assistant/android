package io.homeassistant.companion.android.tiles

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl
import io.homeassistant.companion.android.home.HomeActivity
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class OpenTileSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("TileSettingsActivity")
        Timber.d(intent.action)
        Timber.d(intent.getIntExtra("tile_id", 0).toString())
//        val tileId = intent.extras?.getInt("com.google.android.clockwork.EXTRA_PROVIDER_CONFIG_TILE_ID")
        val tileId = intent.extras?.getInt("tile_id")
        Timber.d(tileId.toString())
        tileId?.takeIf { it != 0 }?.let {
            val settingsIntent = when (intent.action) {
                "ConfigCameraTile" ->
                    HomeActivity.getCameraTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                "ConfigShortcutsTile" -> {
                    lifecycleScope.launch {
                        wearPrefsRepository.getTileShortcutsAndSaveTileId(tileId)
                    }
                    HomeActivity.getShortcutsTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                }
                "ConfigTemplateTile" -> {
                    lifecycleScope.launch {
                        wearPrefsRepository.getTemplateTileAndSaveTileId(tileId)
                    }
                    HomeActivity.getTemplateTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                }
                "ConfigThermostatTile" ->
                    HomeActivity.getThermostatTileSettingsIntent(
                        context = this,
                        tileId = it,
                    )
                else -> null
            }
            Timber.d("selected")
            settingsIntent?.let { startActivity(settingsIntent) }
        }
        Timber.d("Finish")
        finish()
    }
}
