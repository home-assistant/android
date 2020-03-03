package io.homeassistant.companion.android.settings.ssid

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.preference.PreferenceDialogFragmentCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.DialogSsidBinding

class SsidDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        fun newInstance(preferenceKey: String): SsidDialogFragment = SsidDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_KEY, preferenceKey)
            }
        }
    }

    private val ssidAdapter = SsidRecyclerViewAdapter()

    @SuppressLint("InflateParams")
    override fun onCreateDialogView(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.dialog_ssid, null, false)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindDialogView(view: View) {
        val binding = DialogSsidBinding.bind(view)
        binding.inputSsid.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    val result = submitSsid(binding.inputSsid.text.toString())
                    if (result) {
                        binding.inputContainer.error = null
                        binding.inputSsid.text = null
                    } else {
                        binding.inputContainer.error = getString(R.string.manage_ssids_input_exists)
                    }
                    result
                }
                else -> false
            }
        }

        binding.rvSsids.adapter = ssidAdapter

        val ssids = getSsidPreference().getSsids()
        ssidAdapter.submitSet(ssids)
    }

    private fun getSsidPreference(): SsidPreference {
        return preference as SsidPreference
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val ssids = ssidAdapter.currentList.toSortedSet()
            val ssidPreference = getSsidPreference()
            if (preference.callChangeListener(ssids)) {
                ssidPreference.setSsids(ssids)
            }
        }
    }

    private fun submitSsid(ssid: String): Boolean {
        val ssids = ssidAdapter.currentList
        if (ssids.contains(ssid)) {
            return false
        }
        ssidAdapter.submitList(ssids + ssid)
        return true
    }
}
