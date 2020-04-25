package io.homeassistant.companion.android.wear.navigation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.widget.drawer.WearableNavigationDrawerView
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.actions.ActionsFragment
import io.homeassistant.companion.android.wear.databinding.ActivityNavigationBinding
import io.homeassistant.companion.android.wear.settings.SettingsFragment
import io.homeassistant.companion.android.wear.util.extensions.viewBinding

class NavigationActivity : AppCompatActivity(), WearableNavigationDrawerView.OnItemSelectedListener {

    private val binding by viewBinding(ActivityNavigationBinding::inflate)

    private val adapter = NavigationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.topDrawer.setAdapter(adapter)
        binding.topDrawer.addOnItemSelectedListener(this)

        val navigationPages = pages
        adapter.submitPages(navigationPages)
        onItemSelected(0)
    }

    private val pages: List<NavigationItem>
        get() = arrayListOf(
            NavigationItem(getString(R.string.page_actions), getDrawable(R.drawable.ic_home_assistant)!!, NavigationPage.ACTIONS),
            NavigationItem(getString(R.string.page_settings), getDrawable(R.drawable.ic_settings)!!, NavigationPage.SETTINGS)
        )

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

}