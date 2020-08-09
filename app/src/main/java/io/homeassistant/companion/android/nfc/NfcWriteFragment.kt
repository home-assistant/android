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
import kotlinx.android.synthetic.main.fragment_nfc_write.*

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class NfcWriteFragment : Fragment() {

    private lateinit var viewModel: NfcViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(requireActivity()).get(NfcViewModel::class.java)

        return inflater.inflate(R.layout.fragment_nfc_write, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nfcWriteTagObserver = Observer<String> {
            tv_instructions_write_nfc.text = getString(R.string.nfc_write_tag_instructions, it)
        }
        viewModel.nfcWriteTagEvent.observe(viewLifecycleOwner, nfcWriteTagObserver)

        val nfcWriteTagDoneObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_EDIT)
        }
        viewModel.nfcWriteTagDoneEvent.observe(viewLifecycleOwner, nfcWriteTagDoneObserver)

        viewModel.postNewUUID()
    }
}
