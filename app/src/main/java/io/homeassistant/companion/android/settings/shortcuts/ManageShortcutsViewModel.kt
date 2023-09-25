package io.homeassistant.companion.android.settings.shortcuts

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.padding
import com.mikepenz.iconics.utils.size
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.IconDialogCompat
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.assist.AssistShortcutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class ManageShortcutsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ShortcutViewModel"
    }

    val app = application
    private var shortcutManager = application.applicationContext.getSystemService<ShortcutManager>()!!
    val canPinShortcuts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutManager.isRequestPinShortcutSupported
    var pinnedShortcuts = shortcutManager.pinnedShortcuts
        .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) }
        .toMutableList()
        private set
    var dynamicShortcuts = mutableListOf<ShortcutInfo>()
        private set

    var servers by mutableStateOf(serverManager.defaultServers)
        private set
    var entities = mutableStateMapOf<Int, List<Entity<*>>>()
        private set

    private val currentServerId = serverManager.getServer()?.id ?: 0

    private val iconIdToName: Map<Int, String> by lazy { IconDialogCompat(app.assets).loadAllIcons() }

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
        updateDynamicShortcuts()
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
            WebViewActivity.newInstance(app, shortcutPath, serverId).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
        intent.action = shortcutPath
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        icon?.let { intent.putExtra("iconName", icon.mdiName) }

        val shortcut = ShortcutInfo.Builder(app, shortcutId)
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutDesc)
            .setIcon(
                if (icon != null) {
                    val bitmap = IconicsDrawable(app, icon).apply {
                        size = IconicsSize.dp(48)
                        padding = IconicsSize.dp(2)
                        colorFilter = PorterDuffColorFilter(ContextCompat.getColor(app, R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                    }.toBitmap()
                    Icon.createWithBitmap(bitmap)
                } else {
                    Icon.createWithResource(app, R.drawable.ic_stat_ic_notification_blue)
                }
            )
            .setIntent(intent)
            .build()

        if (shortcutId.startsWith("shortcut")) {
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
            updateDynamicShortcuts()
        } else {
            var isNewPinned = true
            for (item in pinnedShortcuts) {
                if (item.id == shortcutId) {
                    isNewPinned = false
                    Log.d(TAG, "Updating pinned shortcut: $shortcutId")
                    shortcutManager.updateShortcuts(listOf(shortcut))
                    Toast.makeText(app, R.string.shortcut_updated, Toast.LENGTH_SHORT).show()
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
        dynamicShortcuts = shortcutManager.dynamicShortcuts.sortedBy { it.id }.toMutableList()
    }

    private fun setDynamicShortcutData(shortcutId: String, index: Int) = viewModelScope.launch {
        if (dynamicShortcuts.isNotEmpty()) {
            for (item in dynamicShortcuts) {
                if (item.id == shortcutId) {
                    Log.d(TAG, "setting ${item.id} data")
                    shortcuts[index].setData(item)
                }
            }
        }
    }

    private suspend fun Shortcut.setData(item: ShortcutInfo) {
        serverId.value = item.intent?.extras?.getInt("server", currentServerId) ?: currentServerId
        label.value = item.shortLabel.toString()
        desc.value = item.longLabel.toString()
        path.value = item.intent?.action.toString()
        selectedIcon.value = if (item.intent?.extras?.containsKey("iconName") == true) {
            item.intent?.extras?.getString("iconName")?.let { CommunityMaterial.getIconByMdiName(it) }
        } else if (item.intent?.extras?.containsKey("iconId") == true) {
            withContext(Dispatchers.IO) {
                item.intent?.extras?.getInt("iconId")?.takeIf { it != 0 }?.let {
                    CommunityMaterial.getIconByMdiName("mdi:${iconIdToName.getValue(it)}")
                }
            }
        } else {
            null
        }
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
