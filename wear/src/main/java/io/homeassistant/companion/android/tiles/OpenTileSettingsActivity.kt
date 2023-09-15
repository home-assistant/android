package io.homeassistant.companion.android.tiles

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepositoryImpl
import io.homeassistant.companion.android.home.HomeActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OpenTileSettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.extras?.getInt("com.google.android.clockwork.EXTRA_PROVIDER_CONFIG_TILE_ID")
        tileId?.takeIf { it != 0 }?.let {
            val settingsIntent = when (intent.action) {
                "ConfigCameraTile" ->
                    HomeActivity.getCameraTileSettingsIntent(
                        context = this,
                        tileId = it
                    )
                "ConfigShortcutsTile" -> {
                    lifecycleScope.launch {
                        wearPrefsRepository.getTileShortcutsAndSaveTileId(tileId)
                    }
                    HomeActivity.getShortcutsTileSettingsIntent(
                        context = this,
                        tileId = it
                    )
                }
                else -> null
            }

            settingsIntent?.let { startActivity(settingsIntent) }
        }
        finish()
    }
}
