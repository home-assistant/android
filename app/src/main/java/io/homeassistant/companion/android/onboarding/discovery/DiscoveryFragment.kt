package io.homeassistant.companion.android.onboarding.discovery

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.R
import kotlinx.android.synthetic.main.fragment_discovery.*

class DiscoveryFragment(private val listener: DiscoveryListener) : Fragment() {

    companion object {
        private const val LOADING_VIEW = 0
        private const val TIMEOUT_VIEW = 1

        fun newInstance(listener: DiscoveryListener): DiscoveryFragment {
            return DiscoveryFragment(listener)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_discovery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        manualSetupButton.setOnClickListener { listener.onSelectManualSetup() }
        retryButton.setOnClickListener { scan() }
    }

    override fun onStart() {
        super.onStart()
        scan()
    }

    private fun scan() {
        flipperView.displayedChild = LOADING_VIEW
        Handler().postDelayed({
            flipperView.displayedChild = TIMEOUT_VIEW
        }, 5000)
    }
}
