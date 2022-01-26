package io.homeassistant.companion.android.settings.shortcuts

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageShortcutsViewModel @Inject constructor(
    private val integrationUseCase: IntegrationRepository,
    application: Application
) : AndroidViewModel(application) {

    private val TAG = "ShortcutViewModel"
    private lateinit var iconPack: IconPack
    private var shortcutManager = application.applicationContext.getSystemService<ShortcutManager>()!!
    val canPinShortcuts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutManager.isRequestPinShortcutSupported
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    var pinnedShortcuts = mutableStateOf(shortcutManager.pinnedShortcuts)
        private set
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    var dynamicShortcuts = mutableStateOf(shortcutManager.dynamicShortcuts)
        private set

    var entities = mutableStateMapOf<String, Entity<*>>()
        private set
    var selectedIcon1 = mutableStateOf(0)
        private set
    var shortcutLabel1 = mutableStateOf("")
        private set
    var shortcutDesc1 = mutableStateOf("")
        private set
    var shortcutPath1 = mutableStateOf("")
        private set
    var shortcutType1 = mutableStateOf("lovelace")
        private set
    var drawableIcon1 = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue))
        private set
    var deleteShortcut1 = mutableStateOf(false)
        private set

    var selectedIcon2 = mutableStateOf(0)
        private set
    var shortcutLabel2 = mutableStateOf("")
        private set
    var shortcutDesc2 = mutableStateOf("")
        private set
    var shortcutPath2 = mutableStateOf("")
        private set
    var shortcutType2 = mutableStateOf("lovelace")
        private set
    var drawableIcon2 = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue))
        private set
    var deleteShortcut2 = mutableStateOf(false)
        private set

    var selectedIcon3 = mutableStateOf(0)
        private set
    var shortcutLabel3 = mutableStateOf("")
        private set
    var shortcutDesc3 = mutableStateOf("")
        private set
    var shortcutPath3 = mutableStateOf("")
        private set
    var shortcutType3 = mutableStateOf("lovelace")
        private set
    var drawableIcon3 = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue))
        private set
    var deleteShortcut3 = mutableStateOf(false)
        private set

    var selectedIcon4 = mutableStateOf(0)
        private set
    var shortcutLabel4 = mutableStateOf("")
        private set
    var shortcutDesc4 = mutableStateOf("")
        private set
    var shortcutPath4 = mutableStateOf("")
        private set
    var shortcutType4 = mutableStateOf("lovelace")
        private set
    var drawableIcon4 = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue))
        private set
    var deleteShortcut4 = mutableStateOf(false)
        private set

    var selectedIcon5 = mutableStateOf(0)
        private set
    var shortcutLabel5 = mutableStateOf("")
        private set
    var shortcutDesc5 = mutableStateOf("")
        private set
    var shortcutPath5 = mutableStateOf("")
        private set
    var shortcutType5 = mutableStateOf("lovelace")
        private set
    var drawableIcon5 = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue))
        private set
    var deleteShortcut5 = mutableStateOf(false)
        private set

    var selectedIconPinned = mutableStateOf(0)
        private set
    var shortcutIdPinned = mutableStateOf("")
        private set
    var shortcutLabelPinned = mutableStateOf("")
        private set
    var shortcutDescPinned = mutableStateOf("")
        private set
    var shortcutPathPinned = mutableStateOf("")
        private set
    var shortcutTypePinned = mutableStateOf("lovelace")
        private set
    var drawableIconPinned = mutableStateOf(AppCompatResources.getDrawable(application, R.drawable.ic_stat_ic_notification_blue))
        private set

    init {
        viewModelScope.launch {
            integrationUseCase.getEntities()?.forEach {
                entities[it.entityId] = it
            }
        }
        Log.d(TAG, "We have ${dynamicShortcuts.value.size} dynamic shortcuts")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Can we pin shortcuts: ${shortcutManager.isRequestPinShortcutSupported}")
            Log.d(TAG, "We have ${pinnedShortcuts.value.size} pinned shortcuts")
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun createShortcut(shortcutId: String, shortcutLabel: String, shortcutDesc: String, shortcutPath: String, bitmap: Bitmap? = null, iconId: Int) {
        Log.d(TAG, "Attempt to add shortcut $shortcutId")
        val intent = Intent(
            WebViewActivity.newInstance(getApplication(), shortcutPath).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
        intent.action = shortcutPath
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.putExtra("iconId", iconId)

        val shortcut = ShortcutInfo.Builder(getApplication(), shortcutId)
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutDesc)
            .setIcon(
                if (bitmap != null)
                    Icon.createWithBitmap(bitmap)
                else
                    Icon.createWithResource(getApplication(), R.drawable.ic_stat_ic_notification_blue)
            )
            .setIntent(intent)
            .build()

        if (shortcutId.startsWith("shortcut")) {
            shortcutManager.addDynamicShortcuts(listOf(shortcut))
            dynamicShortcuts.value = shortcutManager.dynamicShortcuts
        } else {
            var isNewPinned = true
            for (item in pinnedShortcuts.value) {
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

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun deleteShortcut(shortcutId: String) {
        shortcutManager.removeDynamicShortcuts(listOf(shortcutId))
        dynamicShortcuts.value = shortcutManager.dynamicShortcuts
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun setPinnedShortcutData(shortcutId: String) {
        for (item in pinnedShortcuts.value) {
            if (item.id == shortcutId) {
                shortcutIdPinned.value = item.id
                shortcutLabelPinned.value = item.shortLabel.toString()
                shortcutDescPinned.value = item.longLabel.toString()
                shortcutPathPinned.value = item.intent?.action.toString()
                selectedIconPinned.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                if (selectedIconPinned.value != 0)
                    drawableIconPinned.value = getTileIcon(selectedIconPinned.value)
                if (item.intent?.action.toString().startsWith("entityId:"))
                    shortcutTypePinned.value = "entityId"
                else
                    shortcutTypePinned.value = "lovelace"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun setDynamicShortcutData(shortcutId: String, index: Int) {
        if (dynamicShortcuts.value.isNotEmpty()) {
            for (item in dynamicShortcuts.value) {
                if (item.id == shortcutId) {
                    when (index) {
                        1 -> {
                            shortcutLabel1.value = item.shortLabel.toString()
                            shortcutDesc1.value = item.longLabel.toString()
                            shortcutPath1.value = item.intent?.action.toString()
                            selectedIcon1.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                            if (selectedIcon1.value != 0)
                                drawableIcon1.value = getTileIcon(selectedIcon1.value)
                            if (item.intent?.action.toString().startsWith("entityId:"))
                                shortcutType1.value = "entityId"
                            else
                                shortcutType1.value = "lovelace"
                        }
                        2 -> {
                            shortcutLabel2.value = item.shortLabel.toString()
                            shortcutDesc2.value = item.longLabel.toString()
                            shortcutPath2.value = item.intent?.action.toString()
                            selectedIcon2.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                            if (selectedIcon2.value != 0)
                                drawableIcon2.value = getTileIcon(selectedIcon2.value)
                            if (item.intent?.action.toString().startsWith("entityId:"))
                                shortcutType2.value = "entityId"
                            else
                                shortcutType2.value = "lovelace"
                        }
                        3 -> {
                            shortcutLabel3.value = item.shortLabel.toString()
                            shortcutDesc3.value = item.longLabel.toString()
                            shortcutPath3.value = item.intent?.action.toString()
                            selectedIcon3.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                            if (selectedIcon3.value != 0)
                                drawableIcon3.value = getTileIcon(selectedIcon3.value)
                            if (item.intent?.action.toString().startsWith("entityId:"))
                                shortcutType3.value = "entityId"
                            else
                                shortcutType3.value = "lovelace"
                        }
                        4 -> {
                            shortcutLabel4.value = item.shortLabel.toString()
                            shortcutDesc4.value = item.longLabel.toString()
                            shortcutPath4.value = item.intent?.action.toString()
                            selectedIcon4.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                            if (selectedIcon4.value != 0)
                                drawableIcon4.value = getTileIcon(selectedIcon4.value)
                            if (item.intent?.action.toString().startsWith("entityId:"))
                                shortcutType4.value = "entityId"
                            else
                                shortcutType4.value = "lovelace"
                        }
                        5 -> {
                            shortcutLabel5.value = item.shortLabel.toString()
                            shortcutDesc5.value = item.longLabel.toString()
                            shortcutPath5.value = item.intent?.action.toString()
                            selectedIcon5.value = item.intent?.extras?.getInt("iconId").toString().toIntOrNull() ?: 0
                            if (selectedIcon5.value != 0)
                                drawableIcon5.value = getTileIcon(selectedIcon5.value)
                            if (item.intent?.action.toString().startsWith("entityId:"))
                                shortcutType5.value = "entityId"
                            else
                                shortcutType5.value = "lovelace"
                        }
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
            return DrawableCompat.wrap(iconDrawable)
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updatePinnedShortcuts() {
        pinnedShortcuts.value = shortcutManager.pinnedShortcuts
    }
}
