package io.homeassistant.companion.android.settings.shortcuts

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.webview.WebViewActivity
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
    private lateinit var iconPack: IconPack
    private var shortcutManager = application.applicationContext.getSystemService<ShortcutManager>()!!
    val canPinShortcuts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutManager.isRequestPinShortcutSupported
    var pinnedShortcuts: MutableList<ShortcutInfo> = shortcutManager.pinnedShortcuts
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
        var selectedIcon: MutableState<Int>,
        var label: MutableState<String>,
        var desc: MutableState<String>,
        var path: MutableState<String>,
        var type: MutableState<String>,
        var drawable: MutableState<Drawable?>,
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
                    mutableStateOf(0),
                    mutableStateOf(""),
                    mutableStateOf(""),
                    mutableStateOf(""),
                    mutableStateOf("lovelace"),
                    mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue)),
                    mutableStateOf(false)
                )
            )
        }

        if (dynamicShortcuts.size > 0) {
            for (i in 0 until dynamicShortcuts.size)
                setDynamicShortcutData(dynamicShortcuts[i].id, i)
        }
    }

    fun createShortcut(shortcutId: String, serverId: Int, shortcutLabel: String, shortcutDesc: String, shortcutPath: String, bitmap: Bitmap? = null, iconId: Int) {
        Log.d(TAG, "Attempt to add shortcut $shortcutId")
        val intent = Intent(
            WebViewActivity.newInstance(getApplication(), shortcutPath, serverId).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
        intent.action = shortcutPath
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        intent.putExtra("iconId", iconId)

        val shortcut = ShortcutInfo.Builder(getApplication(), shortcutId)
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutDesc)
            .setIcon(
                if (bitmap != null) {
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
                shortcuts.last().serverId.value = item.intent?.extras?.getInt("server", currentServerId) ?: currentServerId
                shortcuts.last().label.value = item.shortLabel.toString()
                shortcuts.last().desc.value = item.longLabel.toString()
                shortcuts.last().path.value = item.intent?.action.toString()
                shortcuts.last().selectedIcon.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                if (shortcuts.last().selectedIcon.value != 0) {
                    shortcuts.last().drawable.value = getTileIcon(shortcuts.last().selectedIcon.value)
                }
                if (shortcuts.last().path.value.startsWith("entityId:")) {
                    shortcuts.last().type.value = "entityId"
                } else {
                    shortcuts.last().type.value = "lovelace"
                }
            }
        }
    }

    fun setDynamicShortcutData(shortcutId: String, index: Int) {
        if (dynamicShortcuts.isNotEmpty()) {
            for (item in dynamicShortcuts) {
                if (item.id == shortcutId) {
                    Log.d(TAG, "setting ${item.id} data")
                    shortcuts[index].serverId.value = item.intent?.extras?.getInt("server", currentServerId) ?: currentServerId
                    shortcuts[index].label.value = item.shortLabel.toString()
                    shortcuts[index].desc.value = item.longLabel.toString()
                    shortcuts[index].path.value = item.intent?.action.toString()
                    shortcuts[index].selectedIcon.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                    if (shortcuts[index].selectedIcon.value != 0) {
                        shortcuts[index].drawable.value = getTileIcon(shortcuts[index].selectedIcon.value)
                    }
                    if (shortcuts[index].path.value.startsWith("entityId:")) {
                        shortcuts[index].type.value = "entityId"
                    } else {
                        shortcuts[index].type.value = "lovelace"
                    }
                }
            }
        }
    }

    private fun getTileIcon(tileIconId: Int): Drawable? {
        val loader = IconPackLoader(getApplication())
        iconPack = createMaterialDesignIconPack(loader)
        iconPack.loadDrawables(loader.drawableLoader)
        val iconDrawable = iconPack.icons[tileIconId]?.drawable
        if (iconDrawable != null) {
            val icon = DrawableCompat.wrap(iconDrawable)
            icon.setColorFilter(app.resources.getColor(R.color.colorAccent), PorterDuff.Mode.SRC_IN)
            return icon
        }
        return null
    }

    fun updatePinnedShortcuts() {
        pinnedShortcuts = shortcutManager.pinnedShortcuts
    }
}
