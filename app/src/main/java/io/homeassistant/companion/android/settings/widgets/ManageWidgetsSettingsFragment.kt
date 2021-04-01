package io.homeassistant.companion.android.settings.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.widgets.button.ButtonWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.template.TemplateWidgetConfigureActivity

class ManageWidgetsSettingsFragment : PreferenceFragmentCompat() {

    companion object {
        fun newInstance(): ManageWidgetsSettingsFragment {
            return ManageWidgetsSettingsFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-widgets"))
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.manage_widgets, rootKey)
    }

    override fun onResume() {
        super.onResume()

        activity?.title = getString(R.string.widgets)
        val staticWidgetDao = AppDatabase.getInstance(requireContext()).staticWidgetDao()
        val staticWidgetList = staticWidgetDao.getAll()
        val templateWidgetDao = AppDatabase.getInstance(requireContext()).templateWidgetDao()
        val templateWidgetList = templateWidgetDao.getAll()
        val buttonWidgetDao = AppDatabase.getInstance(requireContext()).buttonWidgetDao()
        val buttonWidgetList = buttonWidgetDao.getAll()
        val mediaPlayerControlsWidgetDao = AppDatabase.getInstance(requireContext()).mediaPlayCtrlWidgetDao()
        val mediaWidgetList = mediaPlayerControlsWidgetDao.getAll()

        if (staticWidgetList.isNullOrEmpty() && templateWidgetList.isNullOrEmpty() &&
            buttonWidgetList.isNullOrEmpty() && mediaWidgetList.isNullOrEmpty()) {
            findPreference<Preference>("no_widgets")?.let {
                it.isVisible = true
            }
            findPreference<PreferenceCategory>("list_entity_state_widgets")?.let {
                it.isVisible = false
            }
            findPreference<PreferenceCategory>("list_template_widgets")?.let {
                it.isVisible = false
            }
            findPreference<PreferenceCategory>("list_button_widgets")?.let {
                it.isVisible = false
            }
            findPreference<PreferenceCategory>("list_media_player_widgets")?.let {
                it.isVisible = false
            }
        } else {

            findPreference<Preference>("no_widgets")?.let {
                it.isVisible = false
            }

            val prefCategoryStatic = findPreference<PreferenceCategory>("list_entity_state_widgets")
            if (!staticWidgetList.isNullOrEmpty()) {
                prefCategoryStatic?.isVisible = true
                reloadStaticWidgets(staticWidgetList, prefCategoryStatic)
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

            val prefCategoryButton =
                findPreference<PreferenceCategory>("list_button_widgets")
            if (!buttonWidgetList.isNullOrEmpty()) {
                prefCategoryButton?.isVisible = true
                reloadButtonWidgets(buttonWidgetList, prefCategoryButton)
            } else {
                findPreference<PreferenceCategory>("list_button_widgets")?.let {
                    it.isVisible = false
                }
            }

            val prefCategoryMedia = findPreference<PreferenceCategory>("list_media_player_widgets")
            if (!mediaWidgetList.isNullOrEmpty()) {
                prefCategoryMedia?.isVisible = true
                reloadMediaPlayerWidgets(mediaWidgetList, prefCategoryMedia)
            } else {
                findPreference<PreferenceCategory>("list_media_player_widgets")?.let {
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

    private fun reloadButtonWidgets(buttonWidgetList: Array<ButtonWidgetEntity>?, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        if (buttonWidgetList != null) {
            for (item in buttonWidgetList) {
                val pref = Preference(preferenceScreen.context)

                pref.key = item.id.toString()
                if (!item.label.isNullOrEmpty()) {
                    pref.title = item.label
                    pref.summary = "${item.domain}.${item.service}"
                } else
                    pref.title = "${item.domain}.${item.service}"
                pref.isIconSpaceReserved = false

                pref.setOnPreferenceClickListener {
                    val intent =
                        Intent(requireContext(), ButtonWidgetConfigureActivity::class.java).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, item.id)
                        }
                    startActivity(intent)
                    return@setOnPreferenceClickListener true
                }

                prefCategory?.addPreference(pref)
            }
        }
    }

    private fun reloadMediaPlayerWidgets(mediaWidgetList: Array<MediaPlayerControlsWidgetEntity>?, prefCategory: PreferenceCategory?) {
        prefCategory?.removeAll()
        if (mediaWidgetList != null) {
            for (item in mediaWidgetList) {
                val pref = Preference(preferenceScreen.context)

                pref.key = item.id.toString()
                if (!item.label.isNullOrEmpty()) {
                    pref.title = item.label
                    pref.summary = item.entityId
                } else
                    pref.title = item.entityId
                pref.isIconSpaceReserved = false

                pref.setOnPreferenceClickListener {
                    val intent = Intent(requireContext(), MediaPlayerControlsWidgetConfigureActivity::class.java).apply {
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
