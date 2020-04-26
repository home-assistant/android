package io.homeassistant.companion.android.wear.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.FragmentSettingsBinding
import io.homeassistant.companion.android.wear.databinding.ViewRecyclerviewBinding
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import io.homeassistant.companion.android.wear.util.extensions.isStarted
import io.homeassistant.companion.android.wear.util.extensions.requirePreference
import javax.inject.Inject

class SettingsFragment : PreferenceFragmentCompat(), SettingsView {

    @Inject lateinit var presenter: SettingsPresenter

    private lateinit var binding: FragmentSettingsBinding

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), requireContext())
            .inject(this)

        setPreferencesFromResource(R.xml.settings, rootKey)

        val resyncButton = requirePreference<Preference>("resync_settings")
        resyncButton.setOnPreferenceClickListener {
            presenter.syncSettings()
            return@setOnPreferenceClickListener true
        }

        presenter.onViewReady()
    }

    override fun onCreateView(infl: LayoutInflater, cont: ViewGroup?, state: Bundle?): View {
        binding = FragmentSettingsBinding.bind(super.onCreateView(infl, cont, state)!!)
        return binding.root
    }

    override fun onCreateRecyclerView(infl: LayoutInflater, parent: ViewGroup, state: Bundle?): RecyclerView {
        val binding = ViewRecyclerviewBinding.inflate(infl, parent, false)
        val recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.isCircularScrollingGestureEnabled = false
        recyclerView.isEdgeItemsCenteringEnabled = false
        return recyclerView
    }

    override fun displaySyncInProgress(inProgress: Boolean) {
        binding.progress.isVisible = inProgress
    }

    override fun showConfirmed(confirmedType: Int, message: Int) {
        if (isStarted) {
            val showDuration = when (confirmedType) {
                ConfirmationActivity.SUCCESS_ANIMATION -> 1000
                ConfirmationActivity.FAILURE_ANIMATION -> 2000
                else -> throw UnsupportedOperationException("Only the success or failure animation are supported!")
            }
            val intent = Intent(requireContext(), ConfirmationActivity::class.java)
                .putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, confirmedType)
                .putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(message))
                .putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, showDuration)
            startActivity(intent)
        }
    }
}