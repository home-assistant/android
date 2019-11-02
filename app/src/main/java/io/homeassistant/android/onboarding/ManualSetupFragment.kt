package io.homeassistant.android.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import io.homeassistant.android.R


class ManualSetupFragment : Fragment() {

    companion object {
        fun newInstance(): ManualSetupFragment {
            return ManualSetupFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manual_setup, container, false).apply {
            findViewById<Button>(R.id.ok).setOnClickListener {
                (activity as ManualSetupListener).onSelectUrl(findViewById<EditText>(R.id.home_assistant_url).text.toString())
            }
        }
    }

}
