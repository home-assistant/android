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
        ssidAdapter.submitSet(ssids)
    }

    private fun addSsidFromInput(): Boolean {
        val input = binding.inputSsid.text?.toString() ?: return false
        val result = if (!input.isBlank()) submitSsid(input) else return false
        if (result) {
            binding.inputContainer.error = null
            binding.inputSsid.text = null
        } else {
            binding.inputContainer.error = getString(R.string.manage_ssids_input_exists)
        }
        return result
    }

    private fun submitSsid(ssid: String): Boolean {
        val ssids = ssidAdapter.currentList
        if (ssids.contains(ssid)) {
            return false
        }
        ssidAdapter.submitList(ssids + ssid)
        return true
    }

    private fun getSsidPreference(): SsidPreference {
        return preference as SsidPreference
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val ssids = ssidAdapter.currentList.toSortedSet()
            val input = binding.inputSsid.text?.toString()
            if (!input.isNullOrBlank()) {
                ssids.add(input)
            }
            val ssidPreference = getSsidPreference()
            if (preference.callChangeListener(ssids)) {
                ssidPreference.setSsids(ssids)
            }
        }
    }
}
