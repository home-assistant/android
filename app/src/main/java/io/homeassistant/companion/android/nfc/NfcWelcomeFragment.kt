package io.homeassistant.companion.android.nfc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import io.homeassistant.companion.android.R
import kotlinx.android.synthetic.main.fragment_nfc_welcome.*

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class NfcWelcomeFragment : Fragment() {

    private lateinit var viewModel: NfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(requireActivity()).get(NfcViewModel::class.java)

        return inflater.inflate(R.layout.fragment_nfc_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nfcReadObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_READ)
        }
        viewModel.nfcReadEvent.observe(viewLifecycleOwner, nfcReadObserver)

        val nfcWriteTagObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_WRITE)
        }
        viewModel.nfcWriteTagEvent.observe(viewLifecycleOwner, nfcWriteTagObserver)

        btn_nfc_read.setOnClickListener {
            findNavController().navigate(R.id.action_NFC_READ)
        }

        btn_nfc_write.setOnClickListener {
            findNavController().navigate(R.id.action_NFC_WRITE)
        }
    }
}
