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
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.action.ActionActivity
import io.homeassistant.companion.android.wear.action.ActionActivityArgs
import io.homeassistant.companion.android.wear.databinding.FragmentActionsBinding
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import javax.inject.Inject

class ActionsFragment : Fragment(), ActionsView {

    @Inject lateinit var presenter: ActionsPresenter

    private lateinit var binding: FragmentActionsBinding

    private val adapter by lazy {
        ActionsAdapter(presenter::onActionClick, ::onActionLongClick)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), requireContext())
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
        recyclerView.isCircularScrollingGestureEnabled = false
        recyclerView.adapter = adapter

        val progress = binding.confirmationProgress
        progress.totalTime = 2000
        progress.setOnClickListener { hideConfirmation() }

        binding.actionButton.setOnClickListener {
            startActivity(Intent(requireContext(), ActionActivity::class.java))
        }

        presenter.onViewReady()
    }

    private fun onActionLongClick(action: WearAction) {
        ActionActivityArgs(action).startActivity(requireActivity())
    }

    override fun onActionsLoaded(actions: List<WearAction>) {
        binding.empty.isVisible = actions.isEmpty()
        adapter.submitList(actions)
    }

    override fun showConfirmation(action: WearAction) {
        val confirmationProgress = binding.confirmationProgress.apply { isVisible = true }
        confirmationProgress.setOnTimerFinishedListener { hideConfirmation(action) }
        confirmationProgress.startTimer()
    }

    override fun hideConfirmation(action: WearAction?) {
        binding.confirmationProgress.apply {
            stopTimer()
            isVisible = false
            presenter.executeAction(action)
        }
    }

    override fun showProgress(show: Boolean) {
        binding.progress.isVisible = show
    }

    override fun showConfirmed(confirmedType: Int, message: Int) {
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

    override fun onDestroy() {
        presenter.finish()
        super.onDestroy()
    }

}