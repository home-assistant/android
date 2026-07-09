package io.homeassistant.companion.android.settings.shortcuts.legacy

import android.app.Application
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.typeface.IIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.settings.shortcuts.HaShortcutManager
import io.homeassistant.companion.android.settings.shortcuts.SHORTCUT_EXTRA_PATH
import io.homeassistant.companion.android.settings.shortcuts.SHORTCUT_EXTRA_SERVER
import io.homeassistant.companion.android.widgets.assist.AssistShortcutActivity
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class ManageShortcutsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val shortcutManager: HaShortcutManager,
    private val getEntitiesForDisplay: GetEntitiesForDisplayUseCase,
    application: Application,
) : AndroidViewModel(application) {

    val app = application

    val canPinShortcuts =
        SdkVersion.isAtLeast(Build.VERSION_CODES.O) && ShortcutManagerCompat.isRequestPinShortcutSupported(app)
    var pinnedShortcuts = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) }
        .toMutableList()
        private set
    var dynamicShortcuts = mutableListOf<ShortcutInfoCompat>()
        private set

    var servers by mutableStateOf(emptyList<Server>())
        private set
    var displayEntities = mutableStateMapOf<Int, EntityDisplayState>()
        private set

    private suspend fun currentServerId() = serverManager.getServer()?.id ?: 0

    data class Shortcut(
        var id: MutableState<String?>,
        var serverId: MutableState<Int>,
        var selectedIcon: MutableState<IIcon?>,
        var label: MutableState<String>,
        var desc: MutableState<String>,
        var path: MutableState<String>,
        var type: MutableState<String>,
        var delete: MutableState<Boolean>,
    )

    var shortcuts = mutableStateListOf<Shortcut>().apply {
        repeat(6) {
            add(
                Shortcut(
                    id = mutableStateOf(""),
                    serverId = mutableIntStateOf(0),
                    selectedIcon = mutableStateOf(null),
                    label = mutableStateOf(""),
                    desc = mutableStateOf(""),
                    path = mutableStateOf(""),
                    type = mutableStateOf("lovelace"),
                    delete = mutableStateOf(false),
                ),
            )
        }
    }
        private set

    init {
        viewModelScope.launch {
            val currentServerId = currentServerId()
            shortcuts.forEach { it.serverId.value = currentServerId }

            val servers = serverManager.servers()
            this@ManageShortcutsViewModel.servers = servers
            servers.forEach { server ->
                launch {
                    getEntitiesForDisplay(serverId = server.id).collect { state ->
                        displayEntities[server.id] = state
                    }
                }
            }

            updateDynamicShortcuts()
            Timber.d("We have ${dynamicShortcuts.size} dynamic shortcuts")

            if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                Timber.d("Can we pin shortcuts: ${ShortcutManagerCompat.isRequestPinShortcutSupported(app)}")
                Timber.d("We have ${pinnedShortcuts.size} pinned shortcuts")
            }

            if (dynamicShortcuts.isNotEmpty()) {
                for (i in 0 until dynamicShortcuts.size) {
                    setDynamicShortcutData(dynamicShortcuts[i].id, i)
                }
            }
        }
    }

    fun createShortcut(
        shortcutId: String,
        serverId: Int,
        shortcutLabel: String,
        shortcutDesc: String,
        shortcutPath: String,
        icon: IIcon?,
    ) {
        Timber.d("Attempt to add shortcut $shortcutId")
        val shortcut = shortcutManager.buildShortcutInfo(
            shortcutId = shortcutId,
            serverId = serverId,
            label = shortcutLabel,
            longLabel = shortcutDesc,
            path = shortcutPath,
            icon = icon,
        )

        if (shortcutId.startsWith("shortcut")) {
            ShortcutManagerCompat.addDynamicShortcuts(app, listOf(shortcut))
            updateDynamicShortcuts()
        } else {
            var isNewPinned = true
            for (item in pinnedShortcuts) {
                if (item.id == shortcutId) {
                    isNewPinned = false
                    Timber.d("Updating pinned shortcut: $shortcutId")
                    ShortcutManagerCompat.updateShortcuts(app, listOf(shortcut))
                    Toast.makeText(app, commonR.string.shortcut_updated, Toast.LENGTH_SHORT).show()
                }
            }

            if (isNewPinned) {
                Timber.d("Requesting to pin shortcut: $shortcutId")
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                    ShortcutManagerCompat.requestPinShortcut(app, shortcut, null)
                }
            }
        }
    }

    fun deleteShortcut(shortcutId: String) {
        ShortcutManagerCompat.removeDynamicShortcuts(app, listOf(shortcutId))
        updateDynamicShortcuts()
    }

    fun setPinnedShortcutData(shortcutId: String) = viewModelScope.launch {
        for (item in pinnedShortcuts) {
            if (item.id == shortcutId) {
                shortcuts.last().id.value = item.id
                shortcuts.last().setData(item)
            }
        }
    }

    private fun updateDynamicShortcuts() {
        dynamicShortcuts =
            ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC).sortedBy {
                it.id
            }.toMutableList()
    }

    private fun setDynamicShortcutData(shortcutId: String, index: Int) = viewModelScope.launch {
        if (dynamicShortcuts.isNotEmpty()) {
            for (item in dynamicShortcuts) {
                if (item.id == shortcutId) {
                    Timber.d("setting ${item.id} data")
                    shortcuts[index].setData(item)
                }
            }
        }
    }

    private suspend fun Shortcut.setData(item: ShortcutInfoCompat) {
        val currentServerId = currentServerId()
        serverId.value = item.intent.extras?.getInt(SHORTCUT_EXTRA_SERVER, currentServerId) ?: currentServerId
        label.value = item.shortLabel.toString()
        desc.value = item.longLabel.toString()
        path.value = item.intent.getStringExtra(SHORTCUT_EXTRA_PATH).orEmpty()
        selectedIcon.value = shortcutManager.resolveIconFromIntent(item.intent)
        if (path.value.startsWith("entityId:")) {
            type.value = "entityId"
        } else {
            type.value = "lovelace"
        }
    }

    fun updatePinnedShortcuts() {
        pinnedShortcuts.clear()
        pinnedShortcuts.addAll(
            ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) },
        )
    }
}
