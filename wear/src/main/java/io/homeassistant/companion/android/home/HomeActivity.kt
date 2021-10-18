package io.homeassistant.companion.android.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.onboarding.integration.MobileAppIntegrationActivity
import javax.inject.Inject

class HomeActivity : AppCompatActivity(), HomeView {

    @Inject
    lateinit var presenter: HomePresenter

    private lateinit var adapter: HomeListAdapter

    companion object {
        private const val TAG = "HomeActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, HomeActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        adapter = HomeListAdapter()
        adapter.onSceneClicked = { entity -> presenter.onEntityClicked(entity) }
        adapter.onButtonClicked = { id -> presenter.onButtonClicked(id) }

        findViewById<WearableRecyclerView>(R.id.home_list)?.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = this@HomeActivity.adapter
        }

        presenter.onViewReady()
    }

    override fun onDestroy() {
        presenter.onFinish()
        super.onDestroy()
    }

    override fun showHomeList(scenes: List<Entity<Any>>, scripts: List<Entity<Any>>, lights: List<Entity<Any>>, covers: List<Entity<Any>>) {
        adapter.scenes.clear()
        adapter.scripts.clear()
        adapter.lights.clear()
        adapter.covers.clear()

        if (scenes.isNotEmpty())
            adapter.scenes.addAll(scenes)

        if (scripts.isNotEmpty())
            adapter.scripts.addAll(scripts)

        if (lights.isNotEmpty())
            adapter.lights.addAll(lights)

        if (covers.isNotEmpty())
            adapter.covers.addAll(covers)

        adapter.notifyDataSetChanged()
    }

    override fun displayOnBoarding() {
        val intent = OnboardingActivity.newInstance(this)
        startActivity(intent)
        finish()
    }

    override fun displayMobileAppIntegration() {
        val intent = MobileAppIntegrationActivity.newInstance(this)
        startActivity(intent)
        finish()
    }
}
