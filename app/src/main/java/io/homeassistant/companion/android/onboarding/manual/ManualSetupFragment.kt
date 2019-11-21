package io.homeassistant.companion.android.onboarding.manual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import kotlinx.android.synthetic.main.fragment_manual_setup.*
import javax.inject.Inject


class ManualSetupFragment(private val listener: ManualSetupListener) : Fragment(), ManualSetupView {

    companion object {
        fun newInstance(listener: ManualSetupListener): ManualSetupFragment {
            return ManualSetupFragment(listener)
        }
    }

    @Inject
    lateinit var presenter: ManualSetupPresenter

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
    ): View? {
        return inflater.inflate(R.layout.fragment_manual_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        okButton.setOnClickListener { presenter.onClickOk(urlEditText.text.toString()) }
    }

    override fun urlSaved() {
        (activity?.application as GraphComponentAccessor).urlUpdated()
        listener.onSelectUrl()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

}
