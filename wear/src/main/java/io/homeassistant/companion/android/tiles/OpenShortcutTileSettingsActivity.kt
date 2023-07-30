package io.homeassistant.companion.android.tiles

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.home.HomeActivity

class OpenShortcutTileSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.extras?.getInt("com.google.android.clockwork.EXTRA_PROVIDER_CONFIG_TILE_ID")
        tileId?.takeIf { it != 0 }?.let {
            val intent = HomeActivity.getShortcutsTileSettingsIntent(
                context = this,
                tileId = it
            )
            startActivity(intent)
        }
        finish()
    }
}
