package io.homeassistant.companion.android.settings.manageinstances

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject

class ManageInstancesFragment : Fragment(), ManageInstanceView,
    ManageInstanceAdapter.InstanceInterface {

    companion object {
        const val TAG = "ManageInstancesFragment"
        fun newInstance() =
            ManageInstancesFragment()
    }

    @Inject
    lateinit var presenter: ManageInstancePresenter
    private var adapter: ManageInstanceAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerPresenterComponent
            .builder()
            .appComponent((activity?.application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)
        adapter = ManageInstanceAdapter(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manage_instances, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById(R.id.instance_recycler_view) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this.context)
        recyclerView.adapter = adapter
        presenter.getInstances()
        view.findViewById<Button>(R.id.add_new_instance_button)
            .setOnClickListener { presenter.addNewInstance() }
    }

    override fun showInstanceList(instances: List<String>) {
        adapter?.instances = instances
        adapter?.notifyDataSetChanged()
    }

    override fun launchInstance() {
        startActivity(WebViewActivity.newInstance(activity!!))
        activity?.finish()
    }

    override fun onInstanceSelected(urlString: String) {
        presenter.switchToInstance(urlString)
    }

    override fun onDeleteInstance(urlString: String) {
        presenter.deleteInstance(urlString)
    }

    override fun addNewInstance() {
        startActivity(Intent(activity, OnboardingActivity::class.java))
        activity?.finish()
    }
}
