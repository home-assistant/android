package io.homeassistant.companion.android.settings.shortcuts

import android.app.Application
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.backgroundColor
import com.mikepenz.iconics.utils.size
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.IconDialogCompat
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import io.homeassistant.companion.android.webview.WebViewActivity
import io.homeassistant.companion.android.widgets.assist.AssistShortcutActivity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.N_MR1)
@HiltViewModel
class ManageShortcutsViewModel @Inject constructor(
    private val serverManager: ServerManager,
    application: Application,
) : AndroidViewModel(application) {

    val app = application

    val canPinShortcuts =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(app)
    var pinnedShortcuts = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) }
        .toMutableList()
        private set
    var dynamicShortcuts = mutableListOf<ShortcutInfoCompat>()
        private set

    var servers by mutableStateOf(serverManager.defaultServers)
        private set
    var entities = mutableStateMapOf<Int, List<Entity>>()
        private set

    private suspend fun currentServerId() = serverManager.getServer()?.id ?: 0

    private val iconIdToName: Map<Int, String> by lazy { IconDialogCompat(app.assets).loadAllIcons() }

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

            serverManager.defaultServers.forEach { server ->
                launch {
                    entities[server.id] = try {
                        serverManager.integrationRepository(server.id).getEntities().orEmpty()
                            .sortedBy { it.entityId }
                    } catch (e: Exception) {
                        Timber.e(e, "Couldn't load entities for server")
                        emptyList()
                    }
                }
            }

            updateDynamicShortcuts()
            Timber.d("We have ${dynamicShortcuts.size} dynamic shortcuts")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Timber.d("Can we pin shortcuts: ${ShortcutManagerCompat.isRequestPinShortcutSupported(app)}")
                Timber.d("We have ${pinnedShortcuts.size} pinned shortcuts")
            }

            if (dynamicShortcuts.size > 0) {
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
        val intent = Intent(
            WebViewActivity.newInstance(app, shortcutPath, serverId).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
        )
        intent.action = shortcutPath
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        icon?.let { intent.putExtra("iconName", icon.mdiName) }

        val shortcut = ShortcutInfoCompat.Builder(app, shortcutId)
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutDesc)
            .setIcon(
                icon?.toAdaptiveIcon()
                    ?: // Use launcher icon that is an AdaptiveIcon so it gets themed properly by the system
                    IconCompat.createWithResource(app, R.mipmap.ic_launcher),
            )
            .setIntent(intent)
            .build()

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    /**
     * Replicate an AdaptiveIcon from a [IIcon] by applying the right measure.
     * It will output an [IconCompat] created with [IconCompat.createWithAdaptiveBitmap] to flag the [android.graphics.Bitmap]
     * as AdaptiveIcon.
     *
     * @see [AdaptiveIconDrawable] for more the details.
     */
    private fun IIcon.toAdaptiveIcon(): IconCompat {
        val iconDrawable = IconicsDrawable(app, this).apply {
            size = IconicsSize.dp(48)
            colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(app, commonR.color.colorAccent),
                PorterDuff.Mode.SRC_IN,
            )
            backgroundColor = IconicsColor.colorInt(Color.TRANSPARENT)
        }

        val adaptiveIconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            108f,
            app.resources.displayMetrics,
        ).toInt()
        val adaptiveBitmap = createBitmap(adaptiveIconSize, adaptiveIconSize)
        val canvas = Canvas(adaptiveBitmap)
        // Use the same color as the foreground of the launcher as background
        canvas.drawColor(ContextCompat.getColor(app, R.color.ic_launcher_foreground))
        // Calculate the position to draw the icon in the center
        val x = (canvas.width - iconDrawable.intrinsicWidth) / 2f
        val y = (canvas.height - iconDrawable.intrinsicHeight) / 2f
        canvas.translate(x, y)
        iconDrawable.draw(canvas)

        return IconCompat.createWithAdaptiveBitmap(adaptiveBitmap)
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
        serverId.value = item.intent.extras?.getInt("server", currentServerId) ?: currentServerId
        label.value = item.shortLabel.toString()
        desc.value = item.longLabel.toString()
        path.value = item.intent.action.toString()
        selectedIcon.value = if (item.intent.extras?.containsKey("iconName") == true) {
            item.intent.extras?.getString("iconName")?.let { CommunityMaterial.getIconByMdiName(it) }
        } else if (item.intent.extras?.containsKey("iconId") == true) {
            withContext(Dispatchers.IO) {
                item.intent.extras?.getInt("iconId")?.takeIf { it != 0 }?.let {
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
            ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .filter { !it.id.startsWith(AssistShortcutActivity.SHORTCUT_PREFIX) },
        )
    }
}
