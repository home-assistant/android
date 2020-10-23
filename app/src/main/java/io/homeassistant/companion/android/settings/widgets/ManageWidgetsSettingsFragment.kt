package io.homeassistant.companion.android.settings.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity

class ManageWidgetsSettingsFragment : PreferenceFragmentCompat() {

    companion object {
        fun newInstance(): ManageWidgetsSettingsFragment {
            return ManageWidgetsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_widgets, rootKey)
    }

    override fun onResume() {
        super.onResume()

        val staticWidgetDao = AppDatabase.getInstance(requireContext()).staticWidgetDao()
        val staticWidgetList = staticWidgetDao.getAll()

        val prefCategory = findPreference<PreferenceCategory>("list_entity_state_widgets")
        if (!staticWidgetList.isNullOrEmpty()) {
            prefCategory?.isVisible = true
            reloadStaticWidgets(staticWidgetList, prefCategory)
        } else {
            findPreference<PreferenceCategory>("list_entity_state_widgets")?.let {
                it.isVisible = false
            }
            findPreference<Preference>("no_widgets")?.let {
                it.isVisible = true
            }
        }
    }

    private fun reloadStaticWidgets(staticWidgetList: Array<StaticWidgetEntity>?, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        if (staticWidgetList != null) {
            for (item in staticWidgetList) {
                val pref = Preference(preferenceScreen.context)

                pref.key = item.id.toString()
                if (!item.label.isNullOrEmpty()) {
                    pref.title = item.label
                    pref.summary = item.entityId + item.stateSeparator + item.attributeIds.orEmpty()
                } else
                    pref.title = item.entityId + item.stateSeparator + item.attributeIds
                pref.isIconSpaceReserved = false

                pref.setOnPreferenceClickListener {
                    val intent = Intent(requireContext(), EntityWidgetConfigureActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, item.id)
                    }
                    startActivity(intent)
                    return@setOnPreferenceClickListener true
                }

                prefCategory?.addPreference(pref)
            }
        }
    }
}
