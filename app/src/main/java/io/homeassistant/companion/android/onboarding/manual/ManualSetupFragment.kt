package io.homeassistant.companion.android.onboarding.manual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject


class ManualSetupFragment : Fragment(), ManualSetupView {

    companion object {
        fun newInstance(): ManualSetupFragment {
            return ManualSetupFragment()
        }
    }

    @Inject lateinit var presenter: ManualSetupPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manual_setup, container, false).apply {
            findViewById<Button>(R.id.ok).setOnClickListener {
                presenter.onClickOk(findViewById<EditText>(R.id.home_assistant_url).text.toString())
            }
        }
    }

    override fun urlSaved() {
        (activity?.application as GraphComponentAccessor).urlUpdated()
        (activity as ManualSetupListener).onSelectUrl()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

}
