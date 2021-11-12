package io.homeassistant.companion.android.settings.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.settings.DaggerSettingsWearComponent
import io.homeassistant.companion.android.settings.SettingsWearViewModel
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class SettingsWearDevice : AppCompatActivity(), DataClient.OnDataChangedListener {

    private val settingsWearViewModel by viewModels<SettingsWearViewModel>()

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    companion object {
        private const val TAG = "SettingsWearDevice"
        private const val CAPABILITY_WEAR_FAVORITES = "send_home_favorites"
        private const val WEAR_DOCS_LINK = "https://companion.home-assistant.io/docs/wear-os/wear-os"
        val supportedDomains = listOf(
            "input_boolean", "light", "switch", "script", "scene"
        )

        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsWearDevice::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerSettingsWearComponent.builder()
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

    private fun sendHomeFavorites(favoritesList: List<String>) = runBlocking {
        Log.d(TAG, "sendHomeFavorites")

        val putDataRequest = PutDataMapRequest.create("/save_home_favorites").run {
            dataMap.putString("favorites", favoritesList.toString())
            setUrgent()
            asPutDataRequest()
        }

        Wearable.getDataClient(this@SettingsWearDevice).putDataItem(putDataRequest).apply {
            addOnSuccessListener { Log.d(TAG, "Successfully sent favorites to wear") }
            addOnFailureListener { Log.d(TAG, "Failed to send favorites to wear") }
        }
    }

    private fun onEntitySelected(checked: Boolean, entityId: String) {
        if (checked)
            settingsWearViewModel.favoriteEntityIds.add(entityId)
        else
            settingsWearViewModel.favoriteEntityIds.remove(entityId)
        sendHomeFavorites(settingsWearViewModel.favoriteEntityIds.toList())
    }

    @Composable
    private fun LoadWearSettings(settingsWearViewModel: SettingsWearViewModel) {
        val entities = settingsWearViewModel.entities
        val favoritesList = settingsWearViewModel.favoriteEntityIds.toList()
        val validEntities = entities.filter { it.key.split(".")[0] in supportedDomains }.values.sortedBy { it.entityId }.toList()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.wear_favorite_entities)) },
                    actions = {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEAR_DOCS_LINK))
                            startActivity(intent)
                        }) {
                            Icon(
                                Icons.Filled.HelpOutline,
                                contentDescription = stringResource(id = R.string.help)
                            )
                        }
                    }
                )
            }
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 10.dp, start = 20.dp, end = 20.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wear_set_favorites),
                        fontWeight = FontWeight.Bold
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
                        modifier = Modifier
                            .padding(15.dp)
                            .clickable {
                                checked = !checked
                                onEntitySelected(checked, favoritesList[index])
                            }
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                onEntitySelected(it, favoritesList[index])
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
                                modifier = Modifier
                                    .padding(15.dp)
                                    .clickable {
                                        onEntitySelected(true, item.entityId)
                                    }
                            ) {
                                Checkbox(
                                    checked = false,
                                    onCheckedChange = {
                                        onEntitySelected(it, item.entityId)
                                    },
                                    modifier = Modifier.padding(end = 5.dp)
                                )
                                Text(
                                    text = item.entityId
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
