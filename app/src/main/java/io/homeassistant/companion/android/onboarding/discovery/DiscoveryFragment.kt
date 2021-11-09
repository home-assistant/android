package io.homeassistant.companion.android.onboarding.discovery

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.databinding.FragmentDiscoveryBinding
import io.homeassistant.companion.android.databinding.InstanceItemBinding
import javax.inject.Inject

class DiscoveryFragment : Fragment(), DiscoveryView {

    companion object {

        private const val TAG = "DiscoveryFragment"
        private const val HOME_ASSISTANT = "https://www.home-assistant.io"
        fun newInstance(): DiscoveryFragment {
            return DiscoveryFragment()
        }
    }

    private val instances = arrayListOf<HomeAssistantInstance>()

    @Inject
    lateinit var presenter: DiscoveryPresenter

    private lateinit var homeAssistantSearcher: HomeAssistantSearcher

    private lateinit var listViewAdapter: ArrayAdapter<HomeAssistantInstance>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        homeAssistantSearcher = HomeAssistantSearcher(
            getSystemService(requireContext(), NsdManager::class.java)!!,
            this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        listViewAdapter = object : ArrayAdapter<HomeAssistantInstance>(requireContext(), R.layout.instance_item, instances) {
            @SuppressLint("InflateParams")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val binding = if (convertView != null) {
                    InstanceItemBinding.bind(convertView)
                } else {
                    InstanceItemBinding.inflate(LayoutInflater.from(context))
                }
                getItem(position)?.let {
                    binding.name.text = it.name
                    binding.url.text = it.url.toString()
                }

                return binding.root
            }
        }

        val binding = FragmentDiscoveryBinding.inflate(inflater, container, false)

        binding.instanceListView.apply {
            adapter = listViewAdapter
            setOnItemClickListener { _, _, position, _ -> presenter.onUrlSelected(instances[position].url) }
        }
        binding.whatIsThis.apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(HOME_ASSISTANT)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to load Home Assistant home page", e)
                    Toast.makeText(context, R.string.what_is_this_crash, Toast.LENGTH_LONG).show()
                }
            }
        }
        binding.manualSetup
            .setOnClickListener { (activity as DiscoveryListener).onSelectManualSetup() }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        homeAssistantSearcher.beginSearch()
    }

    override fun onPause() {
        super.onPause()
        homeAssistantSearcher.stopSearch()
    }

    override fun onDestroy() {
        super.onDestroy()
        homeAssistantSearcher.stopSearch()
        presenter.onFinish()
    }

    override fun onUrlSaved() {
        (activity as DiscoveryListener).onHomeAssistantDiscover()
    }

    override fun onInstanceFound(instance: HomeAssistantInstance) {
        if (!instances.contains(instance)) {
            instances.add(instance)
            activity?.runOnUiThread {
                listViewAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onScanError() {
        Toast.makeText(context, R.string.failed_scan, Toast.LENGTH_LONG).show()
        if (instances.isEmpty()) {
            (activity as DiscoveryListener).onSelectManualSetup()
        }
    }
}
