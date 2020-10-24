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
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity

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
        val templateWidgetDao = AppDatabase.getInstance(requireContext()).templateWidgetDao()
        val templateWidgetList = templateWidgetDao.getAll()
        if (staticWidgetList.isNullOrEmpty() && templateWidgetList.isNullOrEmpty()) {
            findPreference<Preference>("no_widgets")?.let {
                it.isVisible = true
            }
        } else {

            val prefCategoryState = findPreference<PreferenceCategory>("list_entity_state_widgets")
            if (!staticWidgetList.isNullOrEmpty()) {
                prefCategoryState?.isVisible = true
                reloadStaticWidgets(staticWidgetList, prefCategoryState)
            } else {
                findPreference<PreferenceCategory>("list_entity_state_widgets")?.let {
                    it.isVisible = false
                }
            }

            val prefCategoryTemplate = findPreference<PreferenceCategory>("list_template_widgets")
            if (!templateWidgetList.isNullOrEmpty()) {
                prefCategoryTemplate?.isVisible = true
                reloadTemplateWidgets(templateWidgetList, prefCategoryTemplate)
            } else {
                findPreference<PreferenceCategory>("list_template_widgets")?.let {
                    it.isVisible = false
                }
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

    private fun reloadTemplateWidgets(templateWidgetList: Array<TemplateWidgetEntity>?, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        if (templateWidgetList != null) {
            for (item in templateWidgetList) {
                val pref = Preference(preferenceScreen.context)

                pref.key = item.id.toString()
                pref.title = item.template.take(100)
                pref.isIconSpaceReserved = false

                pref.setOnPreferenceClickListener {
                    val intent = Intent(requireContext(), TemplateWidgetConfigureActivity::class.java).apply {
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
