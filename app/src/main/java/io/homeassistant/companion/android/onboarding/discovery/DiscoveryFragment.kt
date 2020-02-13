package io.homeassistant.companion.android.onboarding.discovery

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import javax.inject.Inject

class DiscoveryFragment : Fragment(), DiscoveryView {

    companion object {

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
            getSystemService(context!!, NsdManager::class.java)!!,
            this
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        listViewAdapter = object : ArrayAdapter<HomeAssistantInstance>(context!!, R.layout.instance_item, instances) {
            @SuppressLint("InflateParams")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context).inflate(
                    R.layout.instance_item,
                    null
                )
                getItem(position)?.let {
                    v.findViewById<AppCompatTextView>(R.id.name).text = it.name
                    v.findViewById<AppCompatTextView>(R.id.url).text = it.url.toString()
                }

                return v
            }
        }

        return inflater.inflate(R.layout.fragment_discovery, container, false).apply {
            findViewById<ListView>(R.id.instance_list_view)?.apply {
                adapter = listViewAdapter
                setOnItemClickListener { _, _, position, _ -> presenter.onUrlSelected(instances[position].url) }
            }

            this.findViewById<Button>(R.id.manual_setup)
                .setOnClickListener { (activity as DiscoveryListener).onSelectManualSetup() }
        }
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
