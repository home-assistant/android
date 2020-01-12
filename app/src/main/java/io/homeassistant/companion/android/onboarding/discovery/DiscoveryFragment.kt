package io.homeassistant.companion.android.onboarding.discovery

import android.net.nsd.NsdManager
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ViewFlipper
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject

class DiscoveryFragment : Fragment(), DiscoveryView {

    companion object {
        private const val LOADING_VIEW = 0
        private const val TIMEOUT_VIEW = 1

        fun newInstance(): DiscoveryFragment {
            return DiscoveryFragment()
        }
    }

    @Inject
    lateinit var presenter: DiscoveryPresenter

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var homeAssistantSearcher: HomeAssistantSearcher

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
        return inflater.inflate(R.layout.fragment_discovery, container, false).apply {
            viewFlipper = this.findViewById(R.id.view_flipper)
            this.findViewById<Button>(R.id.manual_setup)
                .setOnClickListener { (activity as DiscoveryListener).onSelectManualSetup() }
            this.findViewById<Button>(R.id.retry).setOnClickListener { scan() }
        }
    }

    override fun onStart() {
        super.onStart()
        homeAssistantSearcher = HomeAssistantSearcher(
            getSystemService(context!!, NsdManager::class.java)!!
        )
        scan()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onFinish()
    }

    override fun onUrlSaved() {
        (activity?.application as GraphComponentAccessor).urlUpdated()
        (activity as DiscoveryListener).onHomeAssistantDiscover()
    }

    private fun scan() {
        viewFlipper.displayedChild = LOADING_VIEW
        homeAssistantSearcher.beginSearch()
        Handler().postDelayed({
            homeAssistantSearcher.stopSearch()
            if (homeAssistantSearcher.foundInstances.isEmpty()) {
                viewFlipper.displayedChild = TIMEOUT_VIEW
            } else {
                // TODO: Make a UI to choose the correct instance, using first for now.
                presenter.onUrlSelected(homeAssistantSearcher.foundInstances[0].url)
            }
        }, 5000)
    }
}
