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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_nfc_edit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class NfcEditFragment : Fragment() {

    val TAG = NfcEditFragment::class.simpleName

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var viewModel: NfcViewModel

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(requireActivity()).get(NfcViewModel::class.java)

        // Inject components
        DaggerProviderComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_nfc_edit, container, false)
    }

    @SuppressLint("SetTextI18n", "HardwareIds")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nfcReadObserver = Observer<String> { uuid ->
            mainScope.launch {
                et_tag_identifier_content.setText(uuid)
                val deviceId = Settings.Secure.getString(requireActivity().contentResolver, Settings.Secure.ANDROID_ID)
                et_tag_example_trigger_content.setText("- platform: event\n  event_type: tag_scanned\n  event_data:\n    device_id: $deviceId\n    tag_id: $uuid")
            }
        }
        viewModel.nfcReadEvent.observe(viewLifecycleOwner, nfcReadObserver)

        btn_tag_duplicate.setOnClickListener {
            viewModel.nfcWriteTagEvent.postValue(et_tag_identifier_content.text.toString())
            findNavController().navigate(R.id.action_NFC_WRITE)
        }

        btn_tag_fire_event.setOnClickListener {
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

        btn_tag_share_example_trigger.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, et_tag_example_trigger_content.text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }
}
