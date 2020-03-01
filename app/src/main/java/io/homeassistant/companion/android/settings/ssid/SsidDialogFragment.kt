package io.homeassistant.companion.android.settings.ssid

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceDialogFragmentCompat
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.databinding.DialogSsidBinding
import io.homeassistant.companion.android.domain.url.UrlUseCase
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SsidDialogFragment : PreferenceDialogFragmentCompat() {

    companion object {
        fun newInstance(preferenceKey: String): SsidDialogFragment = SsidDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_KEY, preferenceKey) }
        }
    }

    @Inject lateinit var urlUseCase: UrlUseCase

    private val ssidAdapter = SsidRecyclerViewAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerPresenterComponent
            .builder()
            .appComponent((requireContext().applicationContext as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialogView(context: Context?): View {
        return LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ssid, null)
    }

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

        lifecycleScope.launch {
            val ssids = withContext(Dispatchers.IO) { urlUseCase.getHomeWifiSsids() }
            ssidAdapter.submitSet(ssids)
        }
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
