package io.homeassistant.companion.android.settings.views

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.DaggerSettingsWearComponent
import io.homeassistant.companion.android.settings.SettingsWearViewModel
import javax.inject.Inject

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

        DaggerSettingsWearComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val activity = this
        setContent {
            val context = LocalContext.current
            MdcTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = LANDING) {
                    composable(FAVORITES) {
                        LoadWearFavoritesSettings(
                            settingsWearViewModel.entities,
                            settingsWearViewModel.favoriteEntityIds.toList(),
                            { b: Boolean, s: String, a: Activity -> settingsWearViewModel.onEntitySelected(b, s, a) },
                            {
                                settingsWearViewModel.favoriteEntityIds.toList().contains(
                                    settingsWearViewModel.favoriteEntityIds.toList()[it]
                                )
                            },
                            activity
                        )
                    }
                    composable(LANDING) {
                        SettingWearLandingView(context, currentNodes, navController)
                    }
                }
            }
        }
        settingsWearViewModel.init(integrationUseCase)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Thread { settingsWearViewModel.findExistingFavorites(this) }.start()
        Thread { settingsWearViewModel.requestFavorites(this) }.start()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ${dataEvents.count}")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    if (item.uri.path?.compareTo("/home_favorites") == 0) {
                        val data = settingsWearViewModel.getFavorites(DataMapItem.fromDataItem(item).dataMap)
                        settingsWearViewModel.saveHomeFavorites(data, item)
                        Log.d(TAG, "onDataChanged: Found home favorites: $data")
                    }
                }
            }
        }
    }
}
