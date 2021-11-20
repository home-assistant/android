package io.homeassistant.companion.android.settings.views

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.SettingsWearViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SettingsWearMainView : AppCompatActivity(), DataClient.OnDataChangedListener {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    companion object {
        private const val TAG = "SettingsWearDevice"
        private var currentNodes = setOf<Node>()
        const val LANDING = "Landing"
        const val FAVORITES = "Favorites"

        fun newInstance(context: Context, wearNodes: Set<Node>): Intent {
            currentNodes = wearNodes
            return Intent(context, SettingsWearMainView::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LoadSettingsHomeView(settingsWearViewModel, currentNodes.first().displayName)
        }
        settingsWearViewModel.init(integrationUseCase)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Thread { settingsWearViewModel.findExistingFavorites() }.start()
        Thread { settingsWearViewModel.requestFavorites() }.start()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // No op
    }
}
