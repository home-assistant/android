package io.homeassistant.companion.android.onboarding

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ViewFlipper
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.R


class DiscoveryFragment : Fragment() {

    companion object {
        private const val LOADING_VIEW = 0
        private const val TIMEOUT_VIEW = 1

        fun newInstance(): DiscoveryFragment {
            return DiscoveryFragment()
        }
    }

    private lateinit var viewFlipper: ViewFlipper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_discovery, container, false).apply {
            viewFlipper = this.findViewById(R.id.view_flipper)
            this.findViewById<Button>(R.id.manual_setup).setOnClickListener { (activity as DiscoveryListener).onSelectManualSetup() }
            this.findViewById<Button>(R.id.retry).setOnClickListener { scan() }
        }
    }

    override fun onStart() {
        super.onStart()
        scan()
    }

    private fun scan() {
        viewFlipper.displayedChild = LOADING_VIEW
        Handler().postDelayed({
            viewFlipper.displayedChild = TIMEOUT_VIEW
        }, 5000)
    }
}
