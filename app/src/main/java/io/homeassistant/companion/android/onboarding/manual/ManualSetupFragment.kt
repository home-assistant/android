package io.homeassistant.companion.android.onboarding.manual

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.databinding.FragmentManualSetupBinding
import javax.inject.Inject

class ManualSetupFragment : Fragment(), ManualSetupView {

    companion object {
        fun newInstance(): ManualSetupFragment {
            return ManualSetupFragment()
        }
    }

    @Inject
    lateinit var presenter: ManualSetupPresenter

    private var binding: FragmentManualSetupBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentManualSetupBinding.inflate(inflater, container, false)

        binding.homeAssistantUrl.addTextChangedListener { text ->
            binding.ok.isEnabled = URLUtil.isValidUrl(text.toString()) && Patterns.WEB_URL.matcher(text.toString()).matches()
        }
        binding.homeAssistantUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitForm()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        binding.ok.setOnClickListener {
            submitForm()
        }

        this.binding = binding
        return binding.root
    }

    private fun submitForm() {
        presenter.onClickOk(binding!!.homeAssistantUrl.text.toString())
    }

    override fun urlSaved() {
        (activity as ManualSetupListener).onSelectUrl()
    }

    override fun displayUrlError() {
        binding?.urlTextLayout?.error =
            getString(R.string.url_parse_error)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }
}
