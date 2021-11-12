package io.homeassistant.companion.android.settings.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.DaggerSettingsComponent
import io.homeassistant.companion.android.settings.SettingsWearViewModel
import javax.inject.Inject

class SettingsWearDevice : AppCompatActivity(), DataClient.OnDataChangedListener {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    companion object {
        private const val TAG = "SettingsWearDevice"
        private const val CAPABILITY_WEAR_FAVORITES = "send_home_favorites"
        val supportedDomains = listOf(
            "input_boolean", "light", "switch", "script", "scene"
        )

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearDevice::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerSettingsComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        setContent {
            MdcTheme {
                LoadWearSettings(settingsWearViewModel)
            }
        }
        settingsWearViewModel.init(integrationUseCase)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Thread { findExistingFavorites() }.start()
        Thread { requestFavorites() }.start()
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
                        val data = getFavorites(DataMapItem.fromDataItem(item).dataMap)
                        settingsWearViewModel.favoriteEntityIds.clear()
                        settingsWearViewModel.favoriteEntityIds.addAll(
                            data.removeSurrounding("[", "]").split(", ").map { it }
                        )
                        Log.d(TAG, "onDataChanged: Found home favorites: $data")
                    }
                }
            }
        }
    }

    private fun getFavorites(map: DataMap): String {
        map.apply {
            return getString("favorites", "")
        }
    }

    private fun requestFavorites() {
        Log.d(TAG, "Requesting favorites")

        val capabilityInfo = Tasks.await(
            Wearable.getCapabilityClient(this)
                .getCapability(CAPABILITY_WEAR_FAVORITES, CapabilityClient.FILTER_REACHABLE)
        )

        capabilityInfo.nodes.forEach { node ->
            Log.d(TAG, "Requesting favorite data")
            Wearable.getMessageClient(this).sendMessage(
                node.id,
                "/send_home_favorites",
                ByteArray(0)
            ).apply {
                addOnSuccessListener { Log.d(TAG, "Request to favorites sent successfully") }
                addOnFailureListener { Log.d(TAG, "Failed to get favorites") }
            }
        }
    }

    private fun findExistingFavorites() {
        Log.d(TAG, "Finding existing favorites")
        Tasks.await(Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/home_favorites"))).apply {
            Log.d(TAG, "Found existing favorites: ${this.count}")
            this.forEach {
                val data = getFavorites(DataMapItem.fromDataItem(this.first()).dataMap)
                Log.d(TAG, "Favorites: $data")
                settingsWearViewModel.favoriteEntityIds.clear()
                settingsWearViewModel.favoriteEntityIds.addAll(
                    data.removeSurrounding("[", "]").split(", ").map { it }
                )
            }
        }
    }

    @Composable
    private fun LoadWearSettings(settingsWearViewModel: SettingsWearViewModel) {
        val entities = settingsWearViewModel.entities
        val favoritesList = settingsWearViewModel.favoriteEntityIds.toList()
        Log.d(TAG, "Found a total entities of ${settingsWearViewModel.entities.size}")
        val validEntities = entities.filter { it.key.split(".")[0] in supportedDomains }.values.sortedBy { it.entityId }.toList()
        Log.d(TAG, "Found a total valid entities of ${validEntities.size}")
        Log.d(TAG, "favorites list: $favoritesList")
        LazyColumn(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 50.dp, start = 20.dp, end = 20.dp)
        ) {
            item {
                Text(
                    text =
                    if (favoritesList.isNotEmpty())
                        "Found a total of: ${favoritesList.size} favorites"
                    else
                        "You have no favorite entities saved in the wear app"
                )
            }
            items(favoritesList.size) { index ->
                var checked by rememberSaveable {
                    mutableStateOf(
                        favoritesList.contains(
                            favoritesList[index]
                        )
                    )
                }
                Row(
                    modifier = Modifier.padding(15.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            if (it)
                                favoritesList.toMutableSet().add(favoritesList[index])
                            else
                                favoritesList.toMutableSet().remove(favoritesList[index])
                        },
                        modifier = Modifier.padding(end = 5.dp)
                    )
                    Text(
                        text = favoritesList[index].replace("[", "").replace("]", "")
                    )
                }
            }
            if (!validEntities.isNullOrEmpty()) {
                items(validEntities.size - favoritesList.size) { index ->
                    val item = validEntities[index]
                    if (!favoritesList.contains(item.entityId)) {
                        Row(
                            modifier = Modifier.padding(15.dp)
                        ) {
                            Checkbox(
                                checked = false,
                                onCheckedChange = {
                                    if (it)
                                        favoritesList.toMutableSet().add(favoritesList[index])
                                    else
                                        favoritesList.toMutableSet().remove(favoritesList[index])
                                },
                                modifier = Modifier.padding(end = 5.dp)
                            )
                            Text(
                                text = item.entityId
                            )
                        }
                        if (favoritesList.contains(item.entityId))
                            Log.d(TAG, "We found a favorite entity: ${item.entityId}")
                    }
                }
            }
        }
    }
}
