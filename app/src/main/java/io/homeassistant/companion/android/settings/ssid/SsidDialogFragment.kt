package io.homeassistant.companion.android.settings.ssid

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceDialogFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.wifi.WifiHelper
import io.homeassistant.companion.android.databinding.DialogSsidBinding
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class SsidDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        fun newInstance(preferenceKey: String): SsidDialogFragment = SsidDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_KEY, preferenceKey)
            }
        }
    }

    @Inject
    lateinit var wifiHelper: WifiHelper

    private lateinit var binding: DialogSsidBinding
    private val ssidAdapter = SsidRecyclerViewAdapter()

    @SuppressLint("InflateParams")
    override fun onCreateDialogView(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.dialog_ssid, null, false)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindDialogView(view: View) {
        binding = DialogSsidBinding.bind(view)

        binding.actionAdd.setOnClickListener { addSsidFromInput() }
        binding.inputSsid.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> addSsidFromInput()
                else -> false
            }
        }

        binding.rvSsids.adapter = ssidAdapter

        val ssids = getSsidPreference().getSsids()
        ssidAdapter.submitSet(ssids.map { SsidRecyclerViewAdapter.SsidEntry(it, isConnectedToSsid(it)) }.toSet())

        val currentSsid = wifiHelper.getWifiSsid().removeSurrounding("\"")
        if (currentSsid.isBlank() || currentSsid in ssids ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && currentSsid == WifiManager.UNKNOWN_SSID)
        ) {
            binding.suggestCurrentSsid.visibility = View.GONE
        } else {
            binding.suggestCurrentSsid.visibility = View.VISIBLE
            binding.suggestCurrentSsid.text = getString(commonR.string.add_ssid_name_suggestion, currentSsid)
            binding.suggestCurrentSsid.setOnClickListener {
                addSsidFromSuggestion()
            }
        }
    }

    private fun addSsidFromInput(): Boolean {
        val input = binding.inputSsid.text?.toString() ?: return false
        val result = if (input.isNotBlank()) submitSsid(input) else return false
        if (result) {
            binding.inputContainer.error = null
            binding.inputSsid.text = null
        } else {
            binding.inputContainer.error = getString(commonR.string.manage_ssids_input_exists)
        }
        return result
    }

    private fun addSsidFromSuggestion(): Boolean {
        val input = wifiHelper.getWifiSsid().removeSurrounding("\"")
        val result = if (input.isNotBlank()) submitSsid(input) else return false
        if (result) {
            binding.suggestCurrentSsid.visibility = View.GONE
        } // else shouldn't happen, so don't do anything
        return result
    }

    private fun submitSsid(ssid: String): Boolean {
        val ssids = ssidAdapter.currentList
        if (ssids.any { it.name == ssid }) {
            return false
        }
        ssidAdapter.submitList(ssids + SsidRecyclerViewAdapter.SsidEntry(ssid, isConnectedToSsid(ssid)))
        return true
    }

    private fun getSsidPreference(): SsidPreference {
        return preference as SsidPreference
    }

    private fun isConnectedToSsid(ssid: String): Boolean {
        return wifiHelper.getWifiSsid().removeSurrounding("\"") == ssid
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val ssids = ssidAdapter.currentList.toMutableList()
            val input = binding.inputSsid.text?.toString()
            if (!input.isNullOrBlank()) {
                ssids.add(SsidRecyclerViewAdapter.SsidEntry(input, isConnectedToSsid(input)))
            }
            val ssidPreference = getSsidPreference()
            if (preference.callChangeListener(ssids)) {
                ssidPreference.setSsids(ssids.map { it.name }.sorted().toSet())
            }
        }
    }
}
