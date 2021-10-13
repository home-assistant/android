package io.homeassistant.companion.android.onboarding

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.onboarding.discovery.DiscoveryFragment
import io.homeassistant.companion.android.onboarding.manual.ManualSetupFragment

class WelcomeFragment : Fragment() {

    companion object {
        fun newInstance(): WelcomeFragment {
            return WelcomeFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false).apply {

            val learnMore = findViewById<TextView>(R.id.learn_more)
            learnMore.movementMethod = LinkMovementMethod.getInstance()
            learnMore.text = Html.fromHtml("<a href='https://www.home-assistant.io'>Learn More</a>")

            findViewById<Button>(R.id.welcome).setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val discoveryFragment = DiscoveryFragment.newInstance()
                    discoveryFragment.retainInstance = true
                    parentFragmentManager
                        .beginTransaction()
                        .replace(R.id.content, discoveryFragment)
                        .addToBackStack("Welcome")
                        .commit()
                } else {
                    val manualFragment = ManualSetupFragment.newInstance()
                    manualFragment.retainInstance = true
                    parentFragmentManager
                        .beginTransaction()
                        .replace(R.id.content, manualFragment)
                        .addToBackStack("Welcome")
                        .commit()
                }
            }
        }
    }
}
