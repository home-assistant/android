package io.homeassistant.companion.android.settings.shortcuts

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.webview.WebViewActivity

class ManageShortcutsSettingsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val MAX_SHORTCUTS = 5
        private const val SHORTCUT_PREFIX = "shortcut"
        private const val LABEL_SUFFIX = "_label"
        private const val DESC_SUFFIX = "_desc"
        private const val PATH_SUFFIX = "_path"
        private const val UPDATE_SUFFIX = "_update"
        private const val CATEGORY_SUFFIX = "_category"
        private const val DELETE_SUFFIX = "_delete"
        private const val TAG = "ManageShortcutFrag"
        fun newInstance(): ManageShortcutsSettingsFragment {
            return ManageShortcutsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_shortcuts, rootKey)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onResume() {
        super.onResume()

        val addNewShortcut = findPreference<PreferenceCategory>("pinned_shortcut_category")
        val shortcutManager = requireContext().getSystemService(ShortcutManager::class.java)
        var pinnedShortcuts = shortcutManager.pinnedShortcuts
        var dynamicShortcuts = shortcutManager.dynamicShortcuts
        Log.d(TAG, "We have ${dynamicShortcuts.size} dynamic shortcuts")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Can we pin shortcuts: ${shortcutManager.isRequestPinShortcutSupported}")
            Log.d(TAG, "We have ${pinnedShortcuts.size} pinned shortcuts")
        }

        for (i in 1..MAX_SHORTCUTS) {
            findPreference<PreferenceCategory>(SHORTCUT_PREFIX + i + CATEGORY_SUFFIX)?.title = "${getString(R.string.shortcut)} $i"
            var shortcutLabel = findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + LABEL_SUFFIX)?.text
            var shortcutDesc = findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + DESC_SUFFIX)?.text
            var shortcutPath = findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + PATH_SUFFIX)?.text
            val addUpdatePreference = findPreference<Preference>(SHORTCUT_PREFIX + i + UPDATE_SUFFIX)
            val deletePreference = findPreference<Preference>(SHORTCUT_PREFIX + i + DELETE_SUFFIX)

            addUpdatePreference?.isEnabled = !(shortcutLabel.isNullOrEmpty() || shortcutDesc.isNullOrEmpty() || shortcutPath.isNullOrEmpty())

            for (item in dynamicShortcuts) {
                if (item.id == SHORTCUT_PREFIX + i) {
                    addUpdatePreference?.title = getString(R.string.update_shortcut)
                    deletePreference?.isVisible = true
                }
            }

            findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + LABEL_SUFFIX)?.let {
                it.title = "${getString(R.string.shortcut)} $i ${getString(R.string.label)}"
                it.dialogTitle = "${getString(R.string.shortcut)} $i ${getString(R.string.label)}"
                it.setOnPreferenceChangeListener { _, newValue ->
                    shortcutLabel = newValue.toString()
                    addUpdatePreference?.isEnabled = !shortcutLabel.isNullOrEmpty() && !shortcutDesc.isNullOrEmpty() && !shortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
                }
            }

            findPreference<EditTextPreference>(SHORTCUT_PREFIX + i + DESC_SUFFIX)?.let {
                it.title = "${getString(R.string.shortcut)} $i ${getString(R.string.description)}"
                it.dialogTitle = "${getString(R.string.shortcut)} $i ${getString(R.string.description)}"
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
                    val shortcut = createShortcut(SHORTCUT_PREFIX + i, shortcutLabel!!, shortcutDesc!!, shortcutPath!!)
                    shortcutManager!!.addDynamicShortcuts(listOf(shortcut))
                }
                dynamicShortcuts = shortcutManager.dynamicShortcuts
                it.title = getString(R.string.update_shortcut)
                deletePreference?.isVisible = true
                return@setOnPreferenceClickListener true
            }

            deletePreference?.setOnPreferenceClickListener {
                Log.d(TAG, "Attempting to delete shortcut #: $i")
                shortcutManager.removeDynamicShortcuts(listOf(SHORTCUT_PREFIX + i))
                dynamicShortcuts = shortcutManager.dynamicShortcuts
                addUpdatePreference?.title = getString(R.string.add_shortcut)
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
            val pinnedList = findPreference<ListPreference>("pinned_shortcut_list")
            val pinnedShortcutIds = pinnedShortcuts.asSequence().map { it.id }.toList()

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
                            findPreference<EditTextPreference>("pinned_shortcut_path")?.text = item.intent?.action
                            pinnedShortcutPath = item.intent?.action
                            pinnedShortcutPref?.title = getString(R.string.update_pinned_shortcut)
                            pinnedShortcutPref?.isEnabled = true
                        }
                    }
                    return@setOnPreferenceChangeListener true
                }
            }

            for (item in pinnedShortcuts) {
                if (item.id == pinnedShortcutId)
                    pinnedShortcutPref?.title = getString(R.string.update_pinned_shortcut)
            }

            findPreference<EditTextPreference>("pinned_shortcut_id")?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    pinnedShortcutId = newValue.toString()
                    var hasId = false
                    for (item in pinnedShortcuts) {
                        if (item.id == pinnedShortcutId) {
                            hasId = true
                            pinnedShortcutPref?.title = getString(R.string.update_pinned_shortcut)
                        }
                    }
                    if (!hasId)
                        pinnedShortcutPref?.title = getString(R.string.pin_shortcut)
                    pinnedShortcutPref?.isEnabled = !pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()
                    return@setOnPreferenceChangeListener true
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

            if (!pinnedShortcutId.isNullOrEmpty() && !pinnedShortcutLabel.isNullOrEmpty() && !pinnedShortcutDesc.isNullOrEmpty() && !pinnedShortcutPath.isNullOrEmpty()) {
                pinnedShortcutPref?.let {
                    it.isEnabled = true
                    it.setOnPreferenceClickListener {

                        Log.d(TAG, "Attempt to add $pinnedShortcutId")
                        val shortcut = createShortcut(pinnedShortcutId!!, pinnedShortcutLabel!!, pinnedShortcutDesc!!, pinnedShortcutPath!!)
                        var isNewPinned = true

                        for (item in pinnedShortcuts) {
                            if (item.id == pinnedShortcutId) {
                                isNewPinned = false
                                Log.d(TAG, "Updating pinned shortcut $pinnedShortcutId")
                                shortcutManager.updateShortcuts(listOf(shortcut))
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
            }
        } else
            addNewShortcut?.isVisible = false
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createShortcut(shortcutId: String, shortcutLabel: String, shortcutDesc: String, shortcutPath: String): ShortcutInfo {
        val intent = Intent(WebViewActivity.newInstance(requireContext(), shortcutPath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        intent.action = shortcutPath

        return ShortcutInfo.Builder(requireContext(), shortcutId)
                .setShortLabel(shortcutLabel)
                .setLongLabel(shortcutDesc)
                .setIcon(Icon.createWithResource(requireContext(), R.drawable.ic_stat_ic_notification_blue))
                .setIntent(intent)
                .build()
    }
}
