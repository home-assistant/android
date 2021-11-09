package io.homeassistant.companion.android.nfc

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.databinding.FragmentNfcEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class NfcEditFragment : Fragment() {

    val TAG = NfcEditFragment::class.simpleName

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var _binding: FragmentNfcEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NfcViewModel by activityViewModels()

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        // Inflate the layout for this fragment
        _binding = FragmentNfcEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n", "HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nfcReadObserver = Observer<String> { uuid ->
            binding.etTagIdentifierContent.setText(uuid)
            val deviceId = Settings.Secure.getString(requireActivity().contentResolver, Settings.Secure.ANDROID_ID)
            binding.etTagExampleTriggerContent.setText("- platform: event\n  event_type: tag_scanned\n  event_data:\n    device_id: $deviceId\n    tag_id: $uuid")
        }
        viewModel.nfcReadEvent.observe(viewLifecycleOwner, nfcReadObserver)

        binding.btnTagDuplicate.setOnClickListener {
            viewModel.nfcWriteTagEvent.postValue(binding.etTagIdentifierContent.text.toString())
            findNavController().navigate(R.id.action_NFC_WRITE)
        }

        binding.btnTagFireEvent.setOnClickListener {
            mainScope.launch {
                val uuid: String = viewModel.nfcReadEvent.value.toString()
                try {
                    integrationUseCase.scanTag(
                        hashMapOf("tag_id" to uuid)
                    )
                    Toast.makeText(activity, R.string.nfc_event_fired_success, Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(activity, R.string.nfc_event_fired_fail, Toast.LENGTH_LONG)
                        .show()
                    Log.e(TAG, "Unable to send tag to Home Assistant.", e)
                }
            }
        }

        binding.btnTagShareExampleTrigger.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, binding.etTagExampleTriggerContent.text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }
}
