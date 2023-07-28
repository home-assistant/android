package io.homeassistant.companion.android.tiles

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.homeassistant.companion.android.home.HomeActivity

class OpenShortcutTileSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentExtras = intent.extras!!.run {
            keySet().associateWith { key -> get(key) }
        }

        Log.d("RUBBERDUCK", "OpenShortcutTileSettingsActivity intentExtras = $intentExtras")

        val intent = HomeActivity.getShortcutsTileSettingsIntent(
            context = this,
            tileId = 1
        )
        startActivity(intent)
        finish()
    }
}
