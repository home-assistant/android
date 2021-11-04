package io.homeassistant.companion.android.nfc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.databinding.FragmentNfcWriteBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class NfcWriteFragment : Fragment() {

    private var _binding: FragmentNfcWriteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NfcViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNfcWriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nfcWriteTagObserver = Observer<String> {
            binding.tvInstructionsWriteNfc.text = getString(R.string.nfc_write_tag_instructions, it)
        }
        viewModel.nfcWriteTagEvent.observe(viewLifecycleOwner, nfcWriteTagObserver)

        val nfcWriteTagDoneObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_EDIT)
        }
        viewModel.nfcWriteTagDoneEvent.observe(viewLifecycleOwner, nfcWriteTagDoneObserver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
