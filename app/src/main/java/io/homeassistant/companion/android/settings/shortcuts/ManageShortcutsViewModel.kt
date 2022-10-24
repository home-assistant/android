package io.homeassistant.companion.android.settings.shortcuts

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.themes.mdiName
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.assist.AssistShortcutActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class ManageShortcutsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    val app = application
    private val TAG = "ShortcutViewModel"
    private var shortcutManager = application.applicationContext.getSystemService<ShortcutManager>()!!
    val canPinShortcuts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutManager.isRequestPinShortcutSupported
    var pinnedShortcuts = shortcutManager.pinnedShortcuts
        .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) }
        .toMutableList()
        private set
    var dynamicShortcuts: MutableList<ShortcutInfo> = shortcutManager.dynamicShortcuts
        private set

    var servers by mutableStateOf(serverManager.defaultServers)
        private set
    var entities = mutableStateMapOf<Int, List<Entity<*>>>()
        private set

    private val currentServerId = serverManager.getServer()?.id ?: 0

    data class Shortcut(
        var id: MutableState<String?>,
        var serverId: MutableState<Int>,
        var selectedIcon: MutableState<IIcon?>,
        var label: MutableState<String>,
        var desc: MutableState<String>,
        var path: MutableState<String>,
        var type: MutableState<String>,
        var delete: MutableState<Boolean>
    )

    var shortcuts = mutableStateListOf<Shortcut>()
        private set

    init {
        viewModelScope.launch {
            serverManager.defaultServers.forEach { server ->
                launch {
                    entities[server.id] = try {
                        serverManager.integrationRepository(server.id).getEntities().orEmpty()
                            .sortedBy { it.entityId }
                    } catch (e: Exception) {
                        Log.e(TAG, "Couldn't load entities for server", e)
                        emptyList()
                    }
                }
            }
        }
        Log.d(TAG, "We have ${dynamicShortcuts.size} dynamic shortcuts")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Can we pin shortcuts: ${shortcutManager.isRequestPinShortcutSupported}")
            Log.d(TAG, "We have ${pinnedShortcuts.size} pinned shortcuts")
        }

        for (i in 0..5) {
            shortcuts.add(
                Shortcut(
                    mutableStateOf(""),
                    mutableStateOf(currentServerId),
                    mutableStateOf(null),
                    mutableStateOf(""),
                    mutableStateOf(""),
                    mutableStateOf(""),
                    mutableStateOf("lovelace"),
                    // mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue)),
                    mutableStateOf(false)
                )
            )
        }

        if (dynamicShortcuts.size > 0) {
            for (i in 0 until dynamicShortcuts.size)
                setDynamicShortcutData(dynamicShortcuts[i].id, i)
        }
    }

    fun createShortcut(shortcutId: String, serverId: Int, shortcutLabel: String, shortcutDesc: String, shortcutPath: String, icon: IIcon?) {
        Log.d(TAG, "Attempt to add shortcut $shortcutId")
        val intent = Intent(
            WebViewActivity.newInstance(getApplication(), shortcutPath, serverId).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
        intent.action = shortcutPath
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        icon?.let { intent.putExtra("iconName", icon.mdiName) }

        val shortcut = ShortcutInfo.Builder(getApplication(), shortcutId)
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutDesc)
            .setIcon(
                if (icon != null) {
                    val bitmap = IconicsDrawable(getApplication(), icon).toBitmap()
                    Icon.createWithBitmap(bitmap)
                } else {
                    Icon.createWithResource(getApplication(), R.drawable.ic_stat_ic_notification_blue)
                }
            )
            .setIntent(intent)
            .build()

        if (shortcutId.startsWith("shortcut")) {
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
            dynamicShortcuts = shortcutManager.dynamicShortcuts
        } else {
            var isNewPinned = true
            for (item in pinnedShortcuts) {
                if (item.id == shortcutId) {
                    isNewPinned = false
                    Log.d(TAG, "Updating pinned shortcut: $shortcutId")
                    shortcutManager.updateShortcuts(listOf(shortcut))
                    Toast.makeText(getApplication(), R.string.shortcut_updated, Toast.LENGTH_SHORT).show()
                }
            }

            if (isNewPinned) {
                Log.d(TAG, "Requesting to pin shortcut: $shortcutId")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    shortcutManager.requestPinShortcut(shortcut, null)
                }
            }
        }
    }

    fun deleteShortcut(shortcutId: String) {
        shortcutManager.removeDynamicShortcuts(listOf(shortcutId))
        dynamicShortcuts = shortcutManager.dynamicShortcuts
    }

    fun setPinnedShortcutData(shortcutId: String) {
        for (item in pinnedShortcuts) {
            if (item.id == shortcutId) {
                shortcuts.last().id.value = item.id
                shortcuts.last().setData(item)
            }
        }
    }

    fun setDynamicShortcutData(shortcutId: String, index: Int) {
        if (dynamicShortcuts.isNotEmpty()) {
            for (item in dynamicShortcuts) {
                if (item.id == shortcutId) {
                    Log.d(TAG, "setting ${item.id} data")
                    shortcuts[index].setData(item)
                }
            }
        }
    }

    private fun Shortcut.setData(item: ShortcutInfo) {
        serverId.value = item.intent?.extras?.getInt("server", currentServerId) ?: currentServerId
        label.value = item.shortLabel.toString()
        desc.value = item.longLabel.toString()
        path.value = item.intent?.action.toString()
        selectedIcon.value = item.intent?.extras?.getString("iconName")?.let { CommunityMaterial.getIcon(it) }
        if (path.value.startsWith("entityId:")) {
            type.value = "entityId"
        } else {
            type.value = "lovelace"
        }
    }

    fun updatePinnedShortcuts() {
        pinnedShortcuts.clear()
        pinnedShortcuts.addAll(
            shortcutManager.pinnedShortcuts
                .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) }
        )
    }
}
