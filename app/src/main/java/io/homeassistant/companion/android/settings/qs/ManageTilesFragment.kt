package io.homeassistant.companion.android.settings.qs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.settings.DaggerSettingsComponent
import java.lang.Exception
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class ManageTilesFragment : PreferenceFragmentCompat() {

    companion object {
        private const val TAG = "TileFragment"
        fun newInstance(): ManageTilesFragment {
            return ManageTilesFragment()
        }
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/integrations/android-tiles"))
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.quick_setting_tiles, rootKey)
    }

    override fun onResume() {
        super.onResume()

        DaggerSettingsComponent.builder()
            .appComponent((activity?.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        activity?.title = getString(R.string.tiles)
        val tileDao = AppDatabase.getInstance(requireContext()).tileDao()
        var tileList = tileDao.getAll()
        var tileLabel = findPreference<EditTextPreference>("tile_label")?.text
        var tileSubtitle = findPreference<EditTextPreference>("tile_subtitle")?.text
        val tileEntityPref = findPreference<ListPreference>("tile_entity")
        var tileId = findPreference<ListPreference>("tile_list")?.value
        val tileSavePref = findPreference<Preference>("tile_save")
        var tileEntity = tileEntityPref?.value
        var entityList = listOf<String>()

        runBlocking {
            try {
                integrationUseCase.getEntities().forEach {
                    val split = it.entityId.split(".")
                    if (split[0].startsWith("script") || split[0].startsWith("scene"))
                        entityList = entityList + it.entityId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch list of entity IDs", e)
            }
        }

        if (entityList.isNotEmpty()) {
            tileEntityPref?.entries = entityList.sorted().toTypedArray()
            tileEntityPref?.entryValues = entityList.sorted().toTypedArray()
        }

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
                        tileSavePref?.isEnabled = !tileEntity.isNullOrEmpty() && !tileLabel.isNullOrEmpty()
                    }
                }
            }
            tileId = newValue.toString()
            return@setOnPreferenceChangeListener true
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
                tileEntity.toString(),
                tileLabel.toString(),
                tileSubtitle
            )
            tileDao.add(tileData)
            tileList = tileDao.getAll()
            Toast.makeText(requireContext(), R.string.tile_updated, Toast.LENGTH_SHORT).show()
            return@setOnPreferenceClickListener true
        }
    }
}
