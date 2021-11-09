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
import io.homeassistant.companion.android.databinding.FragmentNfcWelcomeBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class NfcWelcomeFragment : Fragment() {

    private var _binding: FragmentNfcWelcomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NfcViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNfcWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nfcReadObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_READ)
        }
        viewModel.nfcReadEvent.observe(viewLifecycleOwner, nfcReadObserver)

        val nfcWriteTagObserver = Observer<String> {
            findNavController().navigate(R.id.action_NFC_WRITE)
        }
        viewModel.nfcWriteTagEvent.observe(viewLifecycleOwner, nfcWriteTagObserver)

        binding.btnNfcRead.setOnClickListener {
            findNavController().navigate(R.id.action_NFC_READ)
        }

        binding.btnNfcWrite.setOnClickListener {
            viewModel.postNewUUID()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
