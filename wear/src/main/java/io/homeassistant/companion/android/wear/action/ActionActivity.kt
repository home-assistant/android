package io.homeassistant.companion.android.wear.action

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.ui.buildArgs
import io.homeassistant.companion.android.wear.databinding.FragmentCreateActionBinding
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import io.homeassistant.companion.android.wear.util.resources.setActionIcon
import javax.inject.Inject

class ActionActivity : AppCompatActivity(), ActionView {

    @Inject lateinit var presenter: ActionPresenter

    private lateinit var args: ActionActivityArgs

    private val binding by viewBinding(FragmentCreateActionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), this)
            .inject(this)

        args = ActionActivityArgs.buildArgs(intent, savedInstanceState)

        binding.actionIcon.setOnClickListener { showIconSelectAlertDialog() }
        binding.actionButton.setOnClickListener {
            val action = buildWearAction() ?: return@setOnClickListener
            presenter.saveAction(action)
        }

        val action = args.action
        if (action != null) {
            binding.heading.setText(R.string.action_header_update)
            binding.actionNameInput.setText(action.name)
            binding.actionActionInput.setText(action.action)
            binding.actionIcon.setActionIcon(action.icon)
        } else {
            binding.heading.setText(R.string.action_header_create)
        }

        presenter.onViewReady()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        args.saveInstance(outState)
    }

    private fun showIconSelectAlertDialog () {
        AlertDialog.Builder(this)
            .setAdapter(IconAdapter(this)) { _, index -> binding.actionIcon.setActionIcon(index) }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun buildWearAction(): WearAction? {
        val name = binding.actionNameInput.text.toString()
        val action = binding.actionActionInput.text.toString()
        val icon = binding.actionIcon.tag.toString().toInt()
        binding.actionNameInput.error = if (name.isBlank()) getString(R.string.action_name_required) else null
        binding.actionActionInput.error = if (action.isBlank()) getString(R.string.action_required) else null
        if (name.isBlank() || action.isBlank()) {
            return null
        }
        return args.action?.copy(icon = icon, name = name, action = action)
            ?: WearAction(icon = icon, name = name, action = action)
    }

    override fun showConfirmed(confirmType: Int, message: Int) {
        val intent = Intent(this, ConfirmationActivity::class.java)
            .putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, confirmType)
            .putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(message))
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        presenter.finish()
        super.onDestroy()
    }

}