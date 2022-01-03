package io.homeassistant.companion.android.settings.shortcuts

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.IconDialogSettings
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManageShortcutsSettingsFragment : PreferenceFragmentCompat(), IconDialog.Callback {

    companion object {
        private const val MAX_SHORTCUTS = 5
        private const val SHORTCUT_PREFIX = "shortcut"
        private const val LABEL_SUFFIX = "_label"
        private const val DESC_SUFFIX = "_desc"
        private const val PATH_SUFFIX = "_path"
        private const val UPDATE_SUFFIX = "_update"
        private const val CATEGORY_SUFFIX = "_category"
        private const val DELETE_SUFFIX = "_delete"
        private const val TYPE_SUFFIX = "_type"
        private const val ENTITY_SUFFIX = "_entity_list"
        private const val ICON_PREFIX = "_icon"
        private const val TAG = "ManageShortcutFrag"
    }

    private lateinit var iconPack: IconPack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-shortcuts"))
        }
    }
    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_shortcuts, rootKey)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onResume() {
        super.onResume()

        val loader = IconPackLoader(requireContext())
        iconPack = createMaterialDesignIconPack(loader)
        iconPack.loadDrawables(loader.drawableLoader)
        val settings = IconDialogSettings {
            searchVisibility = IconDialog.SearchVisibility.ALWAYS
        }
        val iconDialog = IconDialog.newInstance(settings)
        val iconId = 62017
        onIconDialogIconsSelected(iconDialog, listOf(iconPack.icons[iconId]!!))

        activity?.title = getString(commonR.string.shortcuts)

        val addNewShortcut = findPreference<PreferenceCategory>("pinned_shortcut_category")
        val shortcutManager = requireContext().getSystemService(ShortcutManager::class.java)
        var pinnedShortcuts = shortcutManager.pinnedShortcuts
        var dynamicShortcuts = shortcutManager.dynamicShortcuts
        var entityList = listOf<String>()

        runBlocking {
            try {
                integrationUseCase.getEntities()?.forEach {
                    entityList = entityList + it.entityId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch list of entity IDs", e)
            }
        }

        Log.d(TAG, "We have ${dynamicShortcuts.size} dynamic shortcuts")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Can we pin shortcuts: ${shortcutManager.isRequestPinShortcutSupported}")
            Log.d(TAG, "We have ${pinnedShortcuts.size} pinned shortcuts")
        }

        for (i in 1..MAX_SHORTCUTS) {
            findPreference<PreferenceCategory>(SHORTCUT_PREFIX + i + CATEGORY_SUFFIX)?.title = "${getString(commonR.string.shortcut)} $i"
            var shortcutLabel = findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + LABEL_SUFFIX)?.text
            var shortcutDesc = findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + DESC_SUFFIX)?.text
            var shortcutPath = findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + PATH_SUFFIX)?.text
            val addUpdatePreference = findPreference<Preference>(SHORTCUT_PREFIX + i + UPDATE_SUFFIX)
            val deletePreference = findPreference<Preference>(SHORTCUT_PREFIX + i + DELETE_SUFFIX)
            val shortcutType = findPreference<ListPreference>(SHORTCUT_PREFIX + i + TYPE_SUFFIX)
            val shortcutEntityList = findPreference<ListPreference>(SHORTCUT_PREFIX + i + ENTITY_SUFFIX)

            if (entityList.isNotEmpty()) {
                shortcutEntityList?.entries = entityList.sorted().toTypedArray()
                shortcutEntityList?.entryValues = entityList.sorted().toTypedArray()
            }

            setDynamicShortcutType(shortcutType?.value.toString(), i)
            shortcutType?.setOnPreferenceChangeListener { _, newValue ->
                setDynamicShortcutType(newValue.toString(), i)
                addUpdatePreference?.isEnabled = !shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()
                return@setOnPreferenceChangeListener true
            }
            shortcutEntityList?.setOnPreferenceChangeListener { _, newValue ->
                shortcutPath = newValue.toString()
                addUpdatePreference?.isEnabled = !shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()
                return@setOnPreferenceChangeListener true
            }
            addUpdatePreference?.isEnabled = !(shortcutLabel.isNullOrEmpty() || shortcutDesc.isNullOrEmpty() && shortcutPath.isNullOrEmpty())

            for (item in dynamicShortcuts) {
                if (item.id == SHORTCUT_PREFIX + i) {
                    addUpdatePreference?.title = getString(commonR.string.update_shortcut)
                    deletePreference?.isVisible = true
                }
            }

            findPreference<Preference>(SHORTCUT_PREFIX + i + ICON_PREFIX)?.let {
                it.setOnPreferenceClickListener {
                    iconDialog.show(childFragmentManager, SHORTCUT_PREFIX + i)
                    return@setOnPreferenceClickListener true
                }
            }

            findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + LABEL_SUFFIX)?.let {
                it.title = "${getString(commonR.string.shortcut)} $i ${getString(commonR.string.label)}"
                it.dialogTitle = "${getString(commonR.string.shortcut)} $i ${getString(commonR.string.label)}"
                it.setOnPreferenceChangeListener { _, newValue ->
                    shortcutLabel = newValue.toString()
                    addUpdatePreference?.isEnabled = !shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + DESC_SUFFIX)?.let {
                it.title = "${getString(commonR.string.shortcut)} $i ${getString(commonR.string.description)}"
                it.dialogTitle = "${getString(commonR.string.shortcut)} $i ${getString(commonR.string.description)}"
                it.setOnPreferenceChangeListener { _, newValue ->
                    shortcutDesc = newValue.toString()
                    addUpdatePreference?.isEnabled = !shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + PATH_SUFFIX)?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    shortcutPath = newValue.toString()
                    addUpdatePreference?.isEnabled = !shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            addUpdatePreference?.setOnPreferenceClickListener {
                Log.d(TAG, "Creating shortcut #: $i")
                if (!shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()) {
                    if (shortcutType?.value == getString(commonR.string.entity_id))
                        shortcutPath = "entityId:${shortcutEntityList?.value}"
                    val shortcut = createShortcut(SHORTCUT_PREFIX + i, shortcutLabel!!, shortcutDesc!!, shortcutPath!!, findPreference<Preference>(SHORTCUT_PREFIX + i + ICON_PREFIX)?.icon?.toBitmap())
                    shortcutManager!!.addDynamicShortcuts(listOf(shortcut))
                }
                dynamicShortcuts = shortcutManager.dynamicShortcuts
                if (it.title == getString(commonR.string.update_shortcut))
                    Toast.makeText(requireContext(), commonR.string.shortcut_updated, Toast.LENGTH_SHORT).show()
                it.title = getString(commonR.string.update_shortcut)
                deletePreference?.isVisible = true
                return@setOnPreferenceClickListener true
            }

            deletePreference?.setOnPreferenceClickListener {
                Log.d(TAG, "Attempting to delete shortcut #: $i")
                shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_PREFIX + i))
                dynamicShortcuts = shortcutManager.dynamicShortcuts
                addUpdatePreference?.title = getString(commonR.string.add_shortcut)
                it.isVisible = false
                return@setOnPreferenceClickListener true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutManager.isRequestPinShortcutSupported) {
            addNewShortcut?.isVisible = true
            var pinnedShortcutId = findPreference<EditTextPreference>("pinned_shortcut_id")?.text
            var pinnedShortcutLabel = findPreference<EditTextPreference>("pinned_shortcut_label")?.text
            var pinnedShortcutDesc = findPreference<EditTextPreference>("pinned_shortcut_desc")?.text
            var pinnedShortcutPath = findPreference<EditTextPreference>("pinned_shortcut_path")?.text
            val pinnedShortcutPref = findPreference<Preference>("pinned_shortcut_pin")
            val pinnedShortcutType = findPreference<ListPreference>("pinned_shortcut_type")
            val pinnedShortcutEntityList = findPreference<ListPreference>("pinned_shortcut_entity_list")
            val pinnedList = findPreference<ListPreference>("pinned_shortcut_list")
            val pinnedShortcutIds = pinnedShortcuts.asSequence().map { it.id }.toList()

            if (entityList.isNotEmpty()) {
                pinnedShortcutEntityList?.entries = entityList.sorted().toTypedArray()
                pinnedShortcutEntityList?.entryValues = entityList.sorted().toTypedArray()
            }

            setPinnedShortcutType(pinnedShortcutType?.value.toString())
            pinnedShortcutType?.setOnPreferenceChangeListener { _, newValue ->
                setPinnedShortcutType(newValue.toString())
                pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                return@setOnPreferenceChangeListener true
            }
            pinnedShortcutEntityList?.setOnPreferenceChangeListener { _, newValue ->
                pinnedShortcutPath = newValue.toString()
                pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                return@setOnPreferenceChangeListener true
            }
            if (pinnedShortcutIds.isNotEmpty()) {
                pinnedList?.isVisible = true
                pinnedList?.entries = pinnedShortcutIds.toTypedArray()
                pinnedList?.entryValues = pinnedShortcutIds.toTypedArray()
                pinnedList?.setOnPreferenceChangeListener { _, newValue ->
                    for (item in pinnedShortcuts) {
                        if (item.id == newValue) {
                            findPreference<EditTextPreference>("pinned_shortcut_id")?.text = item.id
                            pinnedShortcutId = item.id
                            findPreference<EditTextPreference>("pinned_shortcut_label")?.text = item.shortLabel.toString()
                            pinnedShortcutLabel = item.shortLabel.toString()
                            findPreference<EditTextPreference>("pinned_shortcut_desc")?.text = item.longLabel.toString()
                            pinnedShortcutDesc = item.longLabel.toString()
                            if (item.intent?.action?.startsWith("entityId:") == true) {
                                val entityId = item.intent?.action?.removePrefix("entityId:")
                                pinnedShortcutPath = entityId
                                pinnedShortcutEntityList?.value = entityId
                                pinnedShortcutType?.value = getString(commonR.string.entity_id)
                                setPinnedShortcutType(getString(commonR.string.entity_id))
                            } else {
                                findPreference<EditTextPreference>("pinned_shortcut_path")?.text = item.intent?.action
                                pinnedShortcutPath = item.intent?.action
                                pinnedShortcutType?.value = getString(commonR.string.lovelace)
                                setPinnedShortcutType(getString(commonR.string.lovelace))
                            }
                            pinnedShortcutPref?.title = getString(commonR.string.update_pinned_shortcut)
                            pinnedShortcutPref?.isEnabled = true
                        }
                    }
                    return@setOnPreferenceChangeListener true
                }
            }

            pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()

            for (item in pinnedShortcuts) {
                if (item.id == pinnedShortcutId)
                    pinnedShortcutPref?.title = getString(commonR.string.update_pinned_shortcut)
            }

            findPreference<EditTextPreference>("pinned_shortcut_id")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    pinnedShortcutId = newValue.toString()
                    var hasId = false
                    for (item in pinnedShortcuts) {
                        if (item.id == pinnedShortcutId) {
                            hasId = true
                            pinnedShortcutPref?.title = getString(commonR.string.update_pinned_shortcut)
                        }
                    }
                    if (!hasId)
                        pinnedShortcutPref?.title = getString(commonR.string.pin_shortcut)
                    pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<Preference>("pinned_shortcut_icon")?.let {
                it.setOnPreferenceClickListener {
                    iconDialog.show(childFragmentManager, "pinned")
                    return@setOnPreferenceClickListener true
                }
            }

            findPreference<EditTextPreference>("pinned_shortcut_label")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    pinnedShortcutLabel = newValue.toString()
                    pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<EditTextPreference>("pinned_shortcut_desc")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    pinnedShortcutDesc = newValue.toString()
                    pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<EditTextPreference>("pinned_shortcut_path")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    pinnedShortcutPath = newValue.toString()
                    pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            pinnedShortcutPref?.let {
                it.setOnPreferenceClickListener {

                    Log.d(TAG, "Attempt to add $pinnedShortcutId")
                    if (pinnedShortcutType?.value == getString(commonR.string.entity_id))
                        pinnedShortcutPath = "entityId:${pinnedShortcutEntityList?.value}"
                    val shortcut = createShortcut(pinnedShortcutId!!, pinnedShortcutLabel!!, pinnedShortcutDesc!!, pinnedShortcutPath!!, findPreference<Preference>("pinned_shortcut_icon")?.icon?.toBitmap())
                    var isNewPinned = true

                    for (item in pinnedShortcuts) {
                        if (item.id == pinnedShortcutId) {
                            isNewPinned = false
                            Log.d(TAG, "Updating pinned shortcut $pinnedShortcutId")
                            shortcutManager.updateShortcuts(listOf(shortcut))
                            Toast.makeText(requireContext(), commonR.string.shortcut_updated, Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (isNewPinned) {
                        Log.d(TAG, "Requesting to pin shortcut $pinnedShortcutId")
                        shortcutManager.requestPinShortcut(shortcut, null)
                    }

                    pinnedShortcuts = shortcutManager.pinnedShortcuts
                    return@setOnPreferenceClickListener true
                }
            }
        } else
            addNewShortcut?.isVisible = false
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createShortcut(shortcutId: String, shortcutLabel: String, shortcutDesc: String, shortcutPath: String, bitmap: Bitmap? = null): ShortcutInfo {
        val intent = Intent(WebViewActivity.newInstance(requireContext(), shortcutPath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        intent.action = shortcutPath
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        return ShortcutInfo.Builder(requireContext(), shortcutId)
            .setShortLabel(shortcutLabel)
            .setLongLabel(shortcutDesc)
            .setIcon(
                if (bitmap != null)
                    Icon.createWithBitmap(bitmap)
                else
                    Icon.createWithResource(requireContext(), commonR.drawable.ic_stat_ic_notification_blue)
            )
            .setIntent(intent)
            .build()
    }

    private fun setPinnedShortcutType(value: String) {
        when (value) {
            getString(commonR.string.entity_id) -> {
                findPreference<EditTextPreference>("pinned_shortcut_path")?.isVisible = false
                findPreference<ListPreference>("pinned_shortcut_entity_list")?.isVisible = true
            }
            getString(commonR.string.lovelace) -> {
                findPreference<EditTextPreference>("pinned_shortcut_path")?.isVisible = true
                findPreference<ListPreference>("pinned_shortcut_entity_list")?.isVisible = false
            }
        }
    }

    private fun setDynamicShortcutType(value: String, position: Int) {
        when (value) {
            getString(commonR.string.entity_id) -> {
                findPreference<EditTextPreference>(SHORTCUT_PREFIX + position + PATH_SUFFIX)?.isVisible = false
                findPreference<ListPreference>(SHORTCUT_PREFIX + position + ENTITY_SUFFIX)?.isVisible = true
            }
            getString(commonR.string.lovelace) -> {
                findPreference<EditTextPreference>(SHORTCUT_PREFIX + position + PATH_SUFFIX)?.isVisible = true
                findPreference<ListPreference>(SHORTCUT_PREFIX + position + ENTITY_SUFFIX)?.isVisible = false
            }
        }
    }

    override val iconDialogIconPack: IconPack
        get() = iconPack

    override fun onIconDialogIconsSelected(dialog: IconDialog, icons: List<com.maltaisn.icondialog.data.Icon>) {
        Log.d(TAG, "Selected icon: ${icons.firstOrNull()}")
        val selectedIcon = icons.firstOrNull()
        if (selectedIcon != null) {
            val iconDrawable = selectedIcon.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                icon.setColorFilter(resources.getColor(R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                when (dialog.tag) {
                    "pinned" -> findPreference<Preference>("pinned_shortcut_icon")?.icon = icon
                    "${SHORTCUT_PREFIX}1" -> findPreference<Preference>(SHORTCUT_PREFIX + "1_icon")?.icon = icon
                    "${SHORTCUT_PREFIX}2" -> findPreference<Preference>(SHORTCUT_PREFIX + "2_icon")?.icon = icon
                    "${SHORTCUT_PREFIX}3" -> findPreference<Preference>(SHORTCUT_PREFIX + "3_icon")?.icon = icon
                    "${SHORTCUT_PREFIX}4" -> findPreference<Preference>(SHORTCUT_PREFIX + "4_icon")?.icon = icon
                    "${SHORTCUT_PREFIX}5" -> findPreference<Preference>(SHORTCUT_PREFIX + "5_icon")?.icon = icon
                }
            }
        }
    }
}
