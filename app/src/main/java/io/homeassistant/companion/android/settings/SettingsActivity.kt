package io.homeassistant.companion.android.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.fragment.app.Fragment
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.DaggerPresenterComponent
import io.homeassistant.companion.android.PresenterModule
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.onboarding.OnboardingActivity
import io.homeassistant.companion.android.settings.manageinstances.ManageInstancesFragment
import javax.inject.Inject


class SettingsActivity : AppCompatActivity(), SettingsView {

    companion object {
        fun newInstance(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }

    @Inject
    lateinit var presenter: SettingsPresenter
    var manageInstancesFragment: ManageInstancesFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        DaggerPresenterComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .presenterModule(PresenterModule(this))
            .build()
            .inject(this)

        findViewById<TextView>(R.id.app_version_text_view).text =
            getString(R.string.app_version_text).format(
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
        findViewById<Button>(R.id.logout_button).setOnClickListener { presenter.logout() }
        findViewById<Button>(R.id.manage_instances_button).setOnClickListener { presenter.addNewInstance() }
    }

    override fun redirectToOnboarding() {
        finish()
        startActivity(Intent(this, OnboardingActivity::class.java))
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate()
            findViewById<Group>(R.id.settings_group).visibility = View.VISIBLE
        } else {
            super.onBackPressed()
        }
    }

    override fun manageInstances() {
        val fragment =
            supportFragmentManager.findFragmentByTag(ManageInstancesFragment.TAG)
        if (fragment == null) {
            manageInstancesFragment = ManageInstancesFragment.newInstance()
            replaceFragmentInActivity(
                manageInstancesFragment as ManageInstancesFragment,
                R.id.manage_instances_fragment_container
            )
        } else {
            replaceFragmentInActivity(
                fragment as ManageInstancesFragment,
                R.id.manage_instances_fragment_container
            )
        }
        findViewById<Group>(R.id.settings_group).visibility = View.GONE
    }

    private fun replaceFragmentInActivity(fragment: Fragment, frameId: Int) {
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(frameId, fragment, fragment.tag)
        ft.addToBackStack(fragment.tag)
        ft.commit()

    }
}
