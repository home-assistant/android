package io.homeassistant.companion.android.wear.actions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.widget.WearableLinearLayoutManager
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.FragmentActionsBinding
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import javax.inject.Inject

class ActionsFragment : Fragment(), ActionsView {

    @Inject lateinit var presenter: ActionsPresenter

    private lateinit var binding: FragmentActionsBinding
    private val adapter by lazy { ActionsAdapter(presenter::onActionClick) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this))
            .inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = binding.recyclerView
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.isEdgeItemsCenteringEnabled = true
        recyclerView.adapter = adapter

        val progress = binding.confirmationProgress
        progress.totalTime = 3000L
        progress.setOnClickListener { progress.stopTimer() }

        presenter.onViewReady()
    }

    override fun onActionsLoaded(actions: List<WearAction>) {
        binding.empty.isVisible = actions.isEmpty()
        adapter.submitList(actions)
    }

    override fun showConfirmation(action: WearAction) {
        val clearConfirmation = binding.clearConfirmationProgress.apply { isVisible = true }
        val confirmationProgress = binding.confirmationProgress
        confirmationProgress.setOnTimerFinishedListener {
            clearConfirmation.isVisible = false
            confirmationProgress.stopTimer()
            presenter.executeAction(action)
        }
        confirmationProgress.startTimer()
    }

    override fun showConfirmed(confirmedType: Int) {
        val message = when (confirmedType) {
            ConfirmationActivity.SUCCESS_ANIMATION -> getString(R.string.confirmation_action_send)
            ConfirmationActivity.FAILURE_ANIMATION -> getString(R.string.confirmation_action_send_failure)
            else -> throw UnsupportedOperationException("Only the success or failure animation are supported!")
        }
        val intent = Intent(requireContext(), ConfirmationActivity::class.java)
            .putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, confirmedType)
            .putExtra(ConfirmationActivity.EXTRA_MESSAGE, message)
        startActivity(intent)
    }

    override fun onDestroy() {
        presenter.finish()
        super.onDestroy()
    }

}