package io.homeassistant.companion.android.nfc

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import io.homeassistant.companion.android.R

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class NfcReadFragment : Fragment(R.layout.fragment_nfc_read) {

    private val viewModel: NfcViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nfcReadObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_EDIT)
        }
        viewModel.nfcReadEvent.observe(viewLifecycleOwner, nfcReadObserver)
    }
}
