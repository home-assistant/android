package io.homeassistant.companion.android.complications

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_COMPLICATION_ID
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_COMPLICATION_TYPE
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_DATA_SOURCE_COMPONENT
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.complications.views.LoadConfigView

@AndroidEntryPoint
class ComplicationConfigActivity : ComponentActivity() {

    private val complicationConfigViewModel by viewModels<ComplicationConfigViewModel>()

    companion object {
        private const val TAG = "EntityStateConfigActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, ComplicationConfigActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getIntExtra(EXTRA_CONFIG_COMPLICATION_ID, -1)
        val type = intent.getIntExtra(EXTRA_CONFIG_COMPLICATION_TYPE, -1)
        val component = intent.getParcelableExtra<ComponentName>(EXTRA_CONFIG_DATA_SOURCE_COMPONENT)
        Log.i(TAG, "Config for id $id of type $type for component ${component?.className}")

        setContent {
            LoadConfigView(
                complicationConfigViewModel
            ) {
                setResult(Activity.RESULT_OK)
                complicationConfigViewModel.selectedEntity?.let {
                    complicationConfigViewModel.addEntityStateComplication(
                        id,
                        it
                    )
                }
                finish()
            }
        }
    }
}
