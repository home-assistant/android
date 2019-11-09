package io.homeassistant.companion.android.onboarding.manual

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.R


class ManualSetupFragment : Fragment(), ManualSetupView {

    companion object {
        fun newInstance(): ManualSetupFragment {
            return ManualSetupFragment()
        }
    }

    lateinit var presenter: ManualSetupPresenter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manual_setup, container, false).apply {
            findViewById<Button>(R.id.ok).setOnClickListener {
                presenter.onClickOk(findViewById<EditText>(R.id.home_assistant_url).text.toString())
            }
        }
    }

    override fun urlSaved() {
        (activity as ManualSetupListener).onSelectUrl()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

}
