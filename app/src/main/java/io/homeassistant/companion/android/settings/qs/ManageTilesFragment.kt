package io.homeassistant.companion.android.settings.qs

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.maltaisn.icondialog.IconDialog
import com.maltaisn.icondialog.IconDialogSettings
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.qs.TileEntity
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ManageTilesFragment constructor(
    val integrationRepository: IntegrationRepository
) : PreferenceFragmentCompat(), IconDialog.Callback {

    companion object {
        private const val TAG = "TileFragment"
        private val validDomains = listOf(
            "cover", "fan", "humidifier", "input_boolean", "light",
            "media_player", "remote", "siren", "scene", "script", "switch"
        )
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
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-quick-settings"))
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.quick_setting_tiles, rootKey)
    }

    override fun onResume() {
        super.onResume()

        val loader = IconPackLoader(requireContext())
        iconPack = createMaterialDesignIconPack(loader)
        iconPack.loadDrawables(loader.drawableLoader)
        val settings = IconDialogSettings {
            searchVisibility = IconDialog.SearchVisibility.ALWAYS
        }
        val iconDialog = IconDialog.newInstance(settings)

        activity?.title = getString(commonR.string.tiles)
        val tileDao = AppDatabase.getInstance(requireContext()).tileDao()
        var tileList = tileDao.getAll()
        var tileLabel = findPreference<EditTextPreference>("tile_label")?.text
        var tileSubtitle = findPreference<EditTextPreference>("tile_subtitle")?.text
        val tileEntityPref = findPreference<ListPreference>("tile_entity")
        var tileId = findPreference<ListPreference>("tile_list")?.value
        val resumeIcon = tileDao.get(tileId!!)?.iconId
        val tileSavePref = findPreference<Preference>("tile_save")
        var tileEntity = tileEntityPref?.value
        var entityList = listOf<String>()

        if (resumeIcon != null) {
            val iconDrawable = iconPack.getIcon(resumeIcon)?.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                icon.setColorFilter(resources.getColor(R.color.colorAccent), PorterDuff.Mode.SRC_IN)
                findPreference<Preference>("tile_icon")?.let {
                    it.icon = icon
                    it.summary = resumeIcon.toString()
                }
            }
        }

        runBlocking {
            try {
                integrationRepository.getEntities().forEach {
                    val split = it.entityId.split(".")
                    if (split[0] in validDomains)
                        entityList = entityList + it.entityId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch list of entity IDs", e)
            }
        }

        val sortedEntities = entityList.sorted().toTypedArray()
        if (entityList.isNotEmpty()) {
            tileEntityPref?.entries = sortedEntities
            tileEntityPref?.entryValues = sortedEntities
        }

        findPreference<ListPreference>("tile_entity")?.isEnabled = entityList.isNotEmpty()
        findPreference<Preference>("tile_missing_entity")?.isVisible = entityList.isEmpty()
        tileSavePref?.isEnabled = !tileEntity.isNullOrEmpty() && !tileLabel.isNullOrEmpty()

        findPreference<ListPreference>("tile_list")?.setOnPreferenceChangeListener { _, newValue ->
            if (!tileList.isNullOrEmpty()) {
                for (item in tileList!!) {
                    if (item.tileId == newValue) {
                        Log.d(TAG, "Loading data for tile ${item.tileId}")
                        findPreference<EditTextPreference>("tile_label")?.text = item.label
                        tileLabel = item.label
                        findPreference<EditTextPreference>("tile_subtitle")?.text = item.subtitle
                        tileSubtitle = item.subtitle
                        findPreference<ListPreference>("tile_entity")?.value = item.entityId
                        tileEntity = item.entityId
                        findPreference<Preference>("tile_icon")?.let {
                            val iconId = item.iconId
                            it.summary = iconId.toString()
                            if (iconId != null) {
                                val iconDrawable = iconPack.getIcon(iconId)?.drawable
                                if (iconDrawable != null) {
                                    val icon = DrawableCompat.wrap(iconDrawable)
                                    icon.setColorFilter(
                                        resources.getColor(R.color.colorAccent),
                                        PorterDuff.Mode.SRC_IN
                                    )
                                    it.icon = icon
                                }
                            }
                        }
                        findPreference<ListPreference>("tile_entity")?.isEnabled = entityList.isNotEmpty()
                        findPreference<Preference>("tile_missing_entity")?.isVisible = entityList.isEmpty()
                        tileSavePref?.isEnabled = !tileEntity.isNullOrEmpty() && !tileLabel.isNullOrEmpty()
                    }
                }
            }
            tileId = newValue.toString()
            return@setOnPreferenceChangeListener true
        }

        findPreference<Preference>("tile_icon")?.let {
            it.setOnPreferenceClickListener {
                iconDialog.show(childFragmentManager, tileId)
                return@setOnPreferenceClickListener true
            }
        }

        findPreference<EditTextPreference>("tile_label")?.setOnPreferenceChangeListener { _, newValue ->
            tileLabel = newValue.toString()
            tileSavePref?.isEnabled = !tileEntity.isNullOrEmpty() && !tileLabel.isNullOrEmpty()
            return@setOnPreferenceChangeListener true
        }

        findPreference<EditTextPreference>("tile_subtitle")?.let {
            it.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            it.setOnPreferenceChangeListener { _, newValue ->
                tileSubtitle = newValue.toString()
                tileSavePref?.isEnabled = !tileEntity.isNullOrEmpty() && !tileLabel.isNullOrEmpty()
                return@setOnPreferenceChangeListener true
            }
        }

        findPreference<ListPreference>("tile_entity")?.setOnPreferenceChangeListener { _, newValue ->
            tileEntity = newValue.toString()
            tileSavePref?.isEnabled = !tileEntity.isNullOrEmpty() && !tileLabel.isNullOrEmpty()
            return@setOnPreferenceChangeListener true
        }

        tileSavePref?.setOnPreferenceClickListener {
            Log.d(TAG, "Saving data for tile $tileId")
            val hasTile = tileDao.get(tileId!!)
            var id = 0
            if (hasTile != null && !tileList.isNullOrEmpty()) {
                for (item in tileList!!) {
                    if (item.id == hasTile.id)
                        id = hasTile.id
                }
            }
            val tileData = TileEntity(
                id,
                tileId!!,
                findPreference<Preference>("tile_icon")?.summary.toString().toIntOrNull(),
                tileEntity.toString(),
                tileLabel.toString(),
                tileSubtitle
            )
            tileDao.add(tileData)
            tileList = tileDao.getAll()
            Toast.makeText(requireContext(), commonR.string.tile_updated, Toast.LENGTH_SHORT).show()
            return@setOnPreferenceClickListener true
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
                findPreference<Preference>("tile_icon")?.let {
                    it.icon = icon
                    it.summary = selectedIcon.id.toString()
                }
            }
        }
    }
}
