package io.homeassistant.companion.android.wear.navigation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.widget.drawer.WearableNavigationDrawerView
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.actions.ActionsFragment
import io.homeassistant.companion.android.wear.databinding.ActivityNavigationBinding
import io.homeassistant.companion.android.wear.settings.SettingsFragment
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import javax.inject.Inject

class NavigationActivity : AppCompatActivity(), NavigationView, WearableNavigationDrawerView.OnItemSelectedListener {

    @Inject lateinit var presenter: NavigationPresenter

    private val binding by viewBinding(ActivityNavigationBinding::inflate)

    private val adapter = NavigationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), this)
            .inject(this)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        adapter.submitPages(presenter.getPages())
        binding.topDrawer.setAdapter(adapter)
        binding.topDrawer.addOnItemSelectedListener(this)

        onItemSelected(0)

        presenter.onViewReady()
    }

    override fun onItemSelected(pos: Int) {
        val item = adapter.getPage(pos)
        val foundFragment = supportFragmentManager.findFragmentByTag(item.page.name)

        val transaction = supportFragmentManager.beginTransaction()
        val visibleFragment = supportFragmentManager.fragments.find { fragment ->
            fragment != null && fragment.isVisible
        }
        if (visibleFragment != null) {
            transaction.detach(visibleFragment)
        }
        if (foundFragment == null) {
            val fragment = when (item.page) {
                NavigationPage.ACTIONS -> ActionsFragment()
                NavigationPage.SETTINGS -> SettingsFragment()
            }
            transaction.add(R.id.containerView, fragment, item.page.name)
        } else {
            transaction.attach(foundFragment)
        }
        transaction.commit()
    }

    override fun displayError(messageId: Int) {
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show()
    }
}