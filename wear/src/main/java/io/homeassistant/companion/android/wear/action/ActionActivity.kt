package io.homeassistant.companion.android.wear.action

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.ActivityActionBinding
import io.homeassistant.companion.android.wear.databinding.ViewRecyclerviewBinding
import io.homeassistant.companion.android.wear.ui.buildArgs
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import javax.inject.Inject
import net.steamcrafted.materialiconlib.MaterialDrawableBuilder.IconValue

class ActionActivity : AppCompatActivity(), ActionView {

    @Inject lateinit var presenter: ActionPresenter

    private lateinit var args: ActionActivityArgs

    private val binding by viewBinding(ActivityActionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        DaggerPresenterComponent.factory()
            .create(appComponent, PresenterModule(this))
            .inject(this)

        args = ActionActivityArgs.buildArgs(intent, savedInstanceState)

        binding.confirmationProgress.apply {
            totalTime = 2000
            setOnClickListener { hideConfirmation() }
        }
        binding.actionIconContainer.setOnClickListener { showIconDialog() }
        binding.actionButton.setOnClickListener {
            val action = buildWearAction() ?: return@setOnClickListener
            presenter.saveAction(action)
        }

        val action = args.action
        if (action != null) {
            binding.heading.setText(R.string.action_header_update)
            binding.actionNameInput.setText(action.name)
            binding.actionActionInput.setText(action.action)
            binding.actionIconName.text = action.icon.name
            binding.actionIconPreview.setIcon(action.icon)
            binding.deleteButton.isVisible = true
            binding.deleteButton.setOnClickListener { showConfirmation(action) }
        } else {
            val icon = IconValue.HOME_ASSISTANT
            binding.heading.setText(R.string.action_header_create)
            binding.actionIconPreview.setIcon(icon)
            binding.actionIconName.text = icon.name
        }

        presenter.onViewReady()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        args.saveInstance(outState)
    }

    private fun buildWearAction(): WearAction? {
        val name = binding.actionNameInput.text.toString()
        val action = binding.actionActionInput.text.toString()
        val iconName = binding.actionIconName.text.toString()
        val icon = IconValue.values().find { icon -> icon.name == iconName }

        binding.actionNameInput.error = if (name.isBlank()) getString(R.string.action_name_required) else null
        binding.actionActionInput.error = if (action.isBlank()) getString(R.string.action_required) else null

        if (name.isBlank() || action.isBlank() || icon == null) {
            if (icon == null) {
                Toast.makeText(this, R.string.action_icon_required, Toast.LENGTH_LONG).show()
            }
            return null
        }

        return args.action?.copy(icon = icon, name = name, action = action)
            ?: WearAction(icon = icon, name = name, action = action)
    }

    private fun showIconDialog() {
        val rvBinding = ViewRecyclerviewBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(rvBinding.root)
            .create()

        val recyclerView = rvBinding.recyclerView
        recyclerView.isEdgeItemsCenteringEnabled = true
        recyclerView.isCircularScrollingGestureEnabled = false
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = IconAdapter { icon ->
            binding.actionIconPreview.setIcon(icon)
            binding.actionIconName.text = icon.name
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showConfirmation(action: WearAction) {
        val confirmationProgress = binding.confirmationProgress.apply { isVisible = true }
        confirmationProgress.setOnTimerFinishedListener {
            hideConfirmation()
            presenter.deleteAction(action)
        }
        confirmationProgress.startTimer()
    }

    private fun hideConfirmation() {
        binding.confirmationProgress.apply {
            stopTimer()
            isVisible = false
        }
    }

    override fun showConfirmed(confirmType: Int, message: Int) {
        val showDuration = when (confirmType) {
            ConfirmationActivity.SUCCESS_ANIMATION -> 1000
            ConfirmationActivity.FAILURE_ANIMATION -> 2000
            else -> throw UnsupportedOperationException("Only the success or failure animation are supported!")
        }
        val intent = Intent(this, ConfirmationActivity::class.java)
            .putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, confirmType)
            .putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(message))
            .putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, showDuration)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        presenter.finish()
        super.onDestroy()
    }
}
