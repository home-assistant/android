package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.MenuItemCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import androidx.preference.forEach
import androidx.preference.iterator
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.util.DisabledLocationHandler
import io.homeassistant.companion.android.util.LocationPermissionInfoHandler
import javax.inject.Inject

class SensorsSettingsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            SensorWorker.start(requireContext())
            totalDisabledSensors = 0
            totalEnabledSensors = 0
            val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
            SensorReceiver.MANAGERS.forEach { managers ->
                managers.getAvailableSensors(requireContext()).forEach { basicSensor ->
                    findPreference<Preference>(basicSensor.id)?.let {
                        val sensorEntity = sensorDao.get(basicSensor.id)
                        if (sensorEntity?.enabled == true) {
                            totalEnabledSensors += 1
                            if (!sensorEntity.state.isNullOrBlank()) {
                                if (basicSensor.unitOfMeasurement.isNullOrBlank()) it.summary = sensorEntity.state
                                else it.summary = sensorEntity.state + " " + basicSensor.unitOfMeasurement
                            } else {
                                it.summary = getString(R.string.enabled)
                            }
                            // TODO: Add the icon from mdi:icon?
                        } else {
                            totalDisabledSensors += 1
                            it.summary = getString(R.string.disabled)
                        }
                    }
                }
            }

            findPreference<PreferenceCategory>("enable_disable_category")?.let {
                it.summary = getString(R.string.manage_all_sensors_summary, (totalDisabledSensors + totalEnabledSensors))
            }

            findPreference<SwitchPreference>("enable_disable_sensors")?.let {
                if (totalDisabledSensors == 0) {
                    it.title = getString(R.string.disable_all_sensors, totalEnabledSensors)
                    it.summary = ""
                    it.isChecked = permissionsAllGranted
                    enableAllSensors = permissionsAllGranted
                    activity?.invalidateOptionsMenu()
                } else {
                    if (totalEnabledSensors == 0)
                        it.title = getString(R.string.enable_all_sensors)
                    else
                        it.title = getString(R.string.enable_remaining_sensors, totalDisabledSensors)
                    it.summary = getString(R.string.enable_all_sensors_summary)
                    it.isChecked = false
                }
            }

            handler.postDelayed(this, 10000)
        }
    }

    companion object {
        private var totalEnabledSensors = 0
        private var totalDisabledSensors = 0
        private var permissionsAllGranted = true
        private var settingsWithLocation = mutableListOf<String>()
        private var enableAllSensors = false
        private var showOnlyEnabledSensors = false

        fun newInstance(): SensorsSettingsFragment {
            return SensorsSettingsFragment()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        setPreferencesFromResource(R.xml.sensors, rootKey)

        findPreference<SwitchPreference>("enable_disable_sensors")?.let {

            it.setOnPreferenceChangeListener { _, newState ->

                settingsWithLocation.clear()
                var permArray: Array<String> = arrayOf()
                val context = requireContext()
                enableAllSensors = newState as Boolean

                val locationEnabled = DisabledLocationHandler.isLocationEnabled(context)

                SensorReceiver.MANAGERS.forEach { managers ->
                    managers.getAvailableSensors(context).forEach { basicSensor ->
                        val requiredPermissions = managers.requiredPermissions(basicSensor.id)

                        val locationPermissionCoarse = DisabledLocationHandler.containsLocationPermission(requiredPermissions, false)
                        val locationPermissionFine = DisabledLocationHandler.containsLocationPermission(requiredPermissions, true)
                        val locationPermission = locationPermissionCoarse || locationPermissionFine

                        var enableSensor = false
                        // Only if one of the options is true, enable the sensor
                        if (!enableAllSensors || // All Sensors switch is disabled
                            !locationPermission || // No location permission used for sensor
                            (locationEnabled && locationPermissionCoarse) || // Coarse location used for sensor and location is enabled in settings
                            (locationEnabled && locationPermissionFine) // Fine location used for sensor and location is enabled in settings
                        ) {
                            enableSensor = enableAllSensors
                        } else {
                            settingsWithLocation.add(getString(basicSensor.name))
                        }

                        if (enableSensor && !managers.checkPermission(requireContext(), basicSensor.id))
                            permArray += requiredPermissions.asList()
                    }
                }

                if (permArray.isNotEmpty()) {

                    if (enableAllSensors) {
                        val locationPermissionCoarse = DisabledLocationHandler.containsLocationPermission(permArray, false)
                        val locationPermissionFine = DisabledLocationHandler.containsLocationPermission(permArray, true)
                        if (locationPermissionCoarse || locationPermissionFine) {
                            LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(context, permArray, continueYesCallback = {
                                requestPermission(permArray)
                            })
                        } else requestPermission(permArray)
                    }
                } else {

                    if (showAdditionalDialogsIfNeeded()) {
                        return@setOnPreferenceChangeListener false
                    }
                }

                requireActivity().invalidateOptionsMenu()
                return@setOnPreferenceChangeListener true
            }
        }

        SensorReceiver.MANAGERS.sortedBy { getString(it.name) }.filter { it.hasSensor(requireContext()) }.forEach { manager ->
            val prefCategory = PreferenceCategory(preferenceScreen.context)
            prefCategory.title = getString(manager.name)
            preferenceScreen.addPreference(prefCategory)

            manager.getAvailableSensors(requireContext()).sortedBy { getString(it.name) }.forEach { basicSensor ->
                val pref = Preference(preferenceScreen.context)
                pref.key = basicSensor.id
                pref.title = getString(basicSensor.name)
                pref.isVisible = !showOnlyEnabledSensors || (showOnlyEnabledSensors && manager.isEnabled(requireContext(), basicSensor.id))
                pref.setOnPreferenceClickListener {
                    parentFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.content,
                            SensorDetailFragment.newInstance(
                                manager,
                                basicSensor,
                                integrationUseCase
                            )
                        )
                        .addToBackStack("Sensor Detail")
                        .commit()
                    return@setOnPreferenceClickListener true
                }

                prefCategory.addPreference(pref)
            }
        }

        if (showOnlyEnabledSensors) showHideGroupsIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Needed to call onPrepareOptionsMenu
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.setGroupVisible(R.id.senor_detail_toolbar_group, true)

        if (enableAllSensors)
            menu.removeItem(R.id.action_filter)

        val searchViewItem = menu.findItem(R.id.action_search)
        val searchView: SearchView = MenuItemCompat.getActionView(searchViewItem) as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()

                return false
            }

            override fun onQueryTextChange(query: String?): Boolean {
                filterSensors(query)
                return false
            }
        })

        if (showOnlyEnabledSensors) {
            val checkable = menu.findItem(R.id.action_show_only_enabled_sensors)
            checkable.isChecked = true
        }

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://companion.home-assistant.io/docs/core/sensors"))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_only_enabled_sensors -> {
                item.isChecked = !item.isChecked
                showOnlyEnabledSensors = item.isChecked

                filterSensors()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun filterSensors(searchQuery: String? = "") {
        SensorReceiver.MANAGERS.filter { m -> m.hasSensor(requireContext()) }.forEach { manager ->
            manager.getAvailableSensors(requireContext()).forEach { sensor ->
                val pref = findPreference<Preference>(sensor.id)
                if (pref != null) {
                    pref.isVisible = true
                    if (!searchQuery.isNullOrBlank()) {
                        val found = pref.title.contains(searchQuery, true)
                        pref.isVisible = found
                    } else {
                        if (showOnlyEnabledSensors) {
                            pref.isVisible = manager.isEnabled(requireContext(), sensor.id)
                        }
                    }
                }
            }
        }
        showHideGroupsIfNeeded()
    }

    private fun showHideGroupsIfNeeded() {
        for (pref in preferenceScreen) {
            val prefGroup = pref as? PreferenceGroup
            if (prefGroup != null) {
                prefGroup.isVisible = false

                prefGroup.forEach { pref ->
                    if (pref.isVisible) {
                        prefGroup.isVisible = true
                        return@forEach
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.sensors)
        filterSensors()
        handler.postDelayed(refresh, 0)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0) {
            enableDisableSensorBasedOnPermission()
            showDisabledLocationWarningIfNeeded()
        }
    }

    private fun requestPermission(permissions: Array<String>) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requestPermissions(
                permissions.toSet()
                    .minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    .toTypedArray(), 0
            )
        } else {
            requestPermissions(permissions, 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 0)
            return
        }

        showAdditionalDialogsIfNeeded()

        findPreference<SwitchPreference>("enable_disable_sensors")?.run {
            permissionsAllGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            this.isChecked = permissionsAllGranted
        }
    }

    private fun showAdditionalDialogsIfNeeded(): Boolean {
        return if (!showNotificationListenerDialogIfNeeded()) {
            enableDisableSensorBasedOnPermission()
            showDisabledLocationWarningIfNeeded()
        } else true
    }

    private fun showNotificationListenerDialogIfNeeded(): Boolean {
        val context = requireContext()
        return if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            startActivityForResult(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), 0)
            true
        } else false
    }

    private fun showDisabledLocationWarningIfNeeded(): Boolean {
        return if (settingsWithLocation.isNotEmpty()) {
            DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), settingsWithLocation.toTypedArray())
            true
        } else false
    }

    private fun enableDisableSensorBasedOnPermission() {
        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

        SensorReceiver.MANAGERS.forEach { managers ->
            managers.getAvailableSensors(requireContext()).forEach { basicSensor ->
                val sensorTurnsOnWithGroupToggle = managers.enableToggleAll(requireContext(), basicSensor.id)
                if (!sensorTurnsOnWithGroupToggle) {
                    return@forEach
                }
                var enableSensor = false
                if (enableAllSensors && sensorTurnsOnWithGroupToggle) {
                    enableSensor = managers.checkPermission(requireContext(), basicSensor.id)
                }
                var sensorEntity = sensorDao.get(basicSensor.id)
                if (sensorEntity != null) {
                    sensorEntity.enabled = enableSensor
                    sensorEntity.lastSentState = ""
                    sensorDao.update(sensorEntity)
                } else {
                    sensorEntity = Sensor(basicSensor.id, enableSensor, false, "")
                    sensorDao.add(sensorEntity)
                }
            }
        }
        filterSensors()
    }
}
