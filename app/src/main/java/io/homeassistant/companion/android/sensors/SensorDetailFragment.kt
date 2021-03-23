package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.preference.contains
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.Setting
import io.homeassistant.companion.android.util.DisabledLocationHandler
import io.homeassistant.companion.android.util.LocationPermissionInfoHandler
import kotlinx.coroutines.runBlocking

class SensorDetailFragment(
    private val sensorManager: SensorManager,
    private val basicSensor: SensorManager.BasicSensor,
    private val integrationUseCase: IntegrationRepository
) :
        PreferenceFragmentCompat() {

    companion object {
        fun newInstance(
            sensorManager: SensorManager,
            basicSensor: SensorManager.BasicSensor,
            integrationUseCase: IntegrationRepository
        ): SensorDetailFragment {
            return SensorDetailFragment(sensorManager, basicSensor, integrationUseCase)
        }
    }

    private lateinit var sensorDao: SensorDao
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            refreshSensorData()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerSensorComponent
                .builder()
                .appComponent((activity?.application as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()

        addPreferencesFromResource(R.xml.sensor_detail)

        findPreference<SwitchPreference>("enabled")?.let {
            val dao = sensorDao.get(basicSensor.id)
            val perm = sensorManager.checkPermission(requireContext(), basicSensor.id)
            if (dao == null && sensorManager.enabledByDefault) {
                it.isChecked = perm
            }
            if (dao != null) {
                it.isChecked = dao.enabled && perm
            }
            updateSensorEntity(it.isChecked)

            it.setOnPreferenceChangeListener { _, newState ->
                val isEnabled = newState as Boolean

                if (isEnabled) {
                    val permissions = sensorManager.requiredPermissions(basicSensor.id)
                    val context = requireContext()
                    val fineLocation = DisabledLocationHandler.containsLocationPermission(permissions, true)
                    val coarseLocation = DisabledLocationHandler.containsLocationPermission(permissions, false)

                    if ((fineLocation || coarseLocation) &&
                            !DisabledLocationHandler.isLocationEnabled(context, fineLocation)) {
                        DisabledLocationHandler.showLocationDisabledWarnDialog(requireActivity(), arrayOf(getString(basicSensor.name)))
                        return@setOnPreferenceChangeListener false
                    } else {
                        if (!sensorManager.checkPermission(context, basicSensor.id)) {
                            if (sensorManager is NetworkSensorManager) {
                                LocationPermissionInfoHandler.showLocationPermInfoDialogIfNeeded(context, permissions, continueYesCallback = {
                                    requestPermissions(permissions)
                                })
                            } else requestPermissions(permissions)

                            return@setOnPreferenceChangeListener false
                        }
                    }
                }

                updateSensorEntity(isEnabled)

                if (isEnabled)
                    sensorManager.requestSensorUpdate(requireContext())
                return@setOnPreferenceChangeListener true
            }
        }
        findPreference<Preference>("description")?.let {
            it.summary = getString(basicSensor.descriptionId)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refresh, 0)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private fun refreshSensorData() {
        SensorWorker.start(requireContext())

        val sensorDao = AppDatabase.getInstance(requireContext()).sensorDao()
        val fullData = sensorDao.getFull(basicSensor.id)
        val sensorSettings = sensorDao.getSettings(basicSensor.id)
        if (fullData?.sensor == null)
            return
        val sensorData = fullData.sensor
        val attributes = fullData.attributes

        findPreference<Preference>("unique_id")?.let {
            it.isCopyingEnabled = true
            it.summary = basicSensor.id
        }
        findPreference<Preference>("state")?.let {
            it.isCopyingEnabled = true
            when {
                !sensorData.enabled ->
                    it.summary = "Disabled"
                sensorData.unitOfMeasurement.isNullOrBlank() ->
                    it.summary = sensorData.state
                else ->
                    it.summary = sensorData.state + " " + sensorData.unitOfMeasurement
            }
        }
        findPreference<Preference>("device_class")?.let {
            if (sensorData.enabled && sensorData.deviceClass != null) {
                it.summary = sensorData.deviceClass
                it.isVisible = true
            } else {
                it.isVisible = false
            }
        }
        findPreference<Preference>("icon")?.let {
            if (sensorData.enabled && sensorData.icon != "") {
                it.summary = sensorData.icon
                it.isVisible = true
            } else {
                it.isVisible = false
            }
        }

        findPreference<PreferenceCategory>("attributes")?.let {
            if (sensorData.enabled && !attributes.isNullOrEmpty()) {
                attributes.forEach { attribute ->
                    val key = "attribute_${attribute.name}"
                    val pref = findPreference(key) ?: Preference(requireContext())
                    pref.isCopyingEnabled = true
                    pref.key = key
                    pref.title = attribute.name
                    pref.summary = attribute.value
                    pref.isIconSpaceReserved = false

                    if (!it.contains(pref)) it.addPreference(pref)
                }
                it.isVisible = true
            } else
                it.isVisible = false
        }

        findPreference<PreferenceCategory>("sensor_settings")?.let {
            if (sensorData.enabled && !sensorSettings.isNullOrEmpty()) {
                sensorSettings.forEach { setting ->
                    val key = "setting_${basicSensor.id}_${setting.name}"
                    if (setting.valueType == "toggle") {
                        val pref = findPreference(key) ?: SwitchPreference(requireContext())
                        pref.key = key
                        pref.isEnabled = setting.enabled
                        pref.title = setting.name
                        pref.isChecked = setting.value == "true"
                        pref.isIconSpaceReserved = false
                        pref.isSingleLineTitle = false
                        pref.setOnPreferenceChangeListener { _, newState ->
                            val isEnabled = newState as Boolean

                            sensorDao.add(Setting(basicSensor.id, setting.name, isEnabled.toString(), "toggle", setting.enabled))
                            sensorManager.requestSensorUpdate(requireContext())
                            return@setOnPreferenceChangeListener true
                        }
                        if (!it.contains(pref)) it.addPreference(pref)
                    } else if (setting.valueType == "list") {
                        val pref = findPreference(key) ?: ListPreference(requireContext())
                        pref.key = key
                        pref.isEnabled = setting.enabled
                        val titleId = resources.getIdentifier(key + "_title", "string", requireContext().packageName)
                        pref.title = resources.getString(titleId)
                        pref.dialogTitle = resources.getString(titleId)
                        val entriesResourceId = resources.getIdentifier(key + "_labels", "array", requireContext().packageName)
                        pref.entries = requireContext().resources.getStringArray(entriesResourceId)
                        val entryValuesResourceId = resources.getIdentifier(key + "_values", "array", requireContext().packageName)
                        pref.entryValues = requireContext().resources.getStringArray(entryValuesResourceId)
                        pref.value = setting.value
                        pref.summary = setting.value
                        pref.isIconSpaceReserved = false
                        pref.isSingleLineTitle = false
                        pref.setOnPreferenceChangeListener { _, newState ->
                            sensorDao.add(Setting(basicSensor.id, setting.name, newState as String, "list", setting.enabled))
                            sensorManager.requestSensorUpdate(requireContext())
                            return@setOnPreferenceChangeListener true
                        }
                        if (!it.contains(pref)) it.addPreference(pref)
                    } else if (setting.valueType == "string" || setting.valueType == "number") {
                        val pref = findPreference(key) ?: EditTextPreference(requireContext())
                        pref.key = key
                        pref.isEnabled = setting.enabled
                        pref.title = setting.name
                        pref.dialogTitle = setting.name
                        pref.isSingleLineTitle = false
                        if (pref.text != null)
                            pref.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                        else {
                            pref.summary = setting.value
                        }
                        if (pref.text != setting.value) {
                            pref.text = setting.value
                        }
                        pref.isIconSpaceReserved = false

                        pref.setOnBindEditTextListener { fieldType ->
                            if (setting.valueType == "number")
                                fieldType.inputType = InputType.TYPE_CLASS_NUMBER
                        }

                        pref.setOnPreferenceChangeListener { _, newValue ->
                            sensorDao.add(
                                    Setting(
                                            basicSensor.id,
                                            setting.name,
                                            newValue as String,
                                            setting.valueType,
                                            setting.enabled
                                    )
                            )
                            sensorManager.requestSensorUpdate(requireContext())
                            return@setOnPreferenceChangeListener true
                        }
                        if (!it.contains(pref)) it.addPreference(pref)
                    } else if (setting.valueType == "list-apps") {
                        val packageManager: PackageManager? = context?.packageManager
                        val packages = packageManager?.getInstalledApplications(PackageManager.GET_META_DATA)
                        val packageName: MutableList<String> = ArrayList()
                        if (packages != null) {
                            for (packageItem in packages) {
                                packageName.add(packageItem.packageName)
                            }
                            packageName.sort()
                        }

                        val pref = createListPreference(key, setting, sensorDao, packageName)
                        if (!it.contains(pref)) it.addPreference(pref)
                    } else if (setting.valueType == "list-bluetooth") {
                        val btDevices = BluetoothUtils.getBluetoothDevices(requireContext()).map { b -> b.name }

                        val pref = createListPreference(key, setting, sensorDao, btDevices)
                        if (!it.contains(pref)) it.addPreference(pref)
                    } else if (setting.valueType == "list-zones") {
                        val zones: List<String>
                        runBlocking {
                            zones = integrationUseCase.getZones().map { z -> z.entityId }
                        }

                        val pref = createListPreference(key, setting, sensorDao, zones)
                        if (!it.contains(pref)) it.addPreference(pref)
                    }
                }
                it.isVisible = true
            } else
                it.isVisible = false
        }
    }

    private fun createListPreference(
        key: String,
        setting: Setting,
        sensorDao: SensorDao,
        entries: List<String>
    ): Preference {

        val pref = findPreference(key)
            ?: MultiSelectListPreference(requireContext())
        pref.key = key
        pref.isEnabled = setting.enabled
        pref.title = setting.name
        pref.entries = entries.toTypedArray()
        pref.entryValues = entries.toTypedArray()
        pref.dialogTitle = setting.name
        pref.isIconSpaceReserved = false
        pref.isSingleLineTitle = false
        pref.setOnPreferenceChangeListener { _, newValue ->
            sensorDao.add(
                Setting(
                    basicSensor.id,
                    setting.name,
                    newValue.toString().replace("[", "").replace("]", ""),
                    setting.valueType,
                    setting.enabled
                )
            )
            sensorManager.requestSensorUpdate(requireContext())
            return@setOnPreferenceChangeListener true
        }
        if (pref.values != null)
            pref.summary = pref.values.toString()
        else
            pref.summary = setting.value

        return pref
    }

    private fun updateSensorEntity(
        isEnabled: Boolean
    ) {
        var sensorEntity = sensorDao.get(basicSensor.id)
        if (sensorEntity != null) {
            sensorEntity.enabled = isEnabled
            sensorEntity.lastSentState = ""
            sensorDao.update(sensorEntity)
        } else {
            sensorEntity = Sensor(basicSensor.id, isEnabled, false, "")
            sensorDao.add(sensorEntity)
        }
        refreshSensorData()
    }

    private fun requestPermissions(permissions: Array<String>) {
        when {
            permissions.any { perm -> perm == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE } ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                requestPermissions(
                        permissions.toSet()
                                .minus(Manifest.permission.ACCESS_BACKGROUND_LOCATION).toTypedArray(), 0
                )
            else -> requestPermissions(permissions, 0)
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
        }

        findPreference<SwitchPreference>("enabled")?.run {
            isChecked = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            updateSensorEntity(isChecked)
        }
    }
}
