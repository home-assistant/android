package io.homeassistant.companion.android.wear.action

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.ActivityActionBinding
import io.homeassistant.companion.android.wear.ui.buildArgs
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import net.steamcrafted.materialiconlib.MaterialDrawableBuilder.IconValue
import java.util.*
import javax.inject.Inject

class ActionActivity : AppCompatActivity(), ActionView {

    @Inject lateinit var presenter: ActionPresenter

    private lateinit var args: ActionActivityArgs

    private val binding by viewBinding(ActivityActionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), this)
            .inject(this)

        args = ActionActivityArgs.buildArgs(intent, savedInstanceState)

        binding.actionButton.setOnClickListener {
            val action = buildWearAction() ?: return@setOnClickListener
            presenter.saveAction(action)
        }

        binding.actionIconInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val icon = findIcon(v.text.toString())
                if (icon == null) {
                    binding.actionIconPreview.setImageDrawable(null)
                } else {
                    binding.actionIconPreview.setIcon(icon)
                }
            }
            return@setOnEditorActionListener false
        }

        val action = args.action
        if (action != null) {
            binding.heading.setText(R.string.action_header_update)
            binding.actionNameInput.setText(action.name)
            binding.actionActionInput.setText(action.action)
            binding.actionIconInput.setText(action.iconRaw)
            binding.actionIconPreview.setIcon(action.icon)
            binding.deleteButton.isVisible = true
            binding.deleteButton.setOnClickListener { presenter.deleteAction(action) }
        } else {
            binding.heading.setText(R.string.action_header_create)
            binding.actionIconPreview.setImageDrawable(null)
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
        val icon = binding.actionIconInput.text.toString()
        val iconValue = findIcon(icon)

        binding.actionNameInput.error = if (name.isBlank()) getString(R.string.action_name_required) else null
        binding.actionActionInput.error = if (action.isBlank()) getString(R.string.action_required) else null
        binding.actionIconInput.error = if (iconValue == null) getString(R.string.action_icon_required)  else null

        if (name.isBlank() || action.isBlank() || iconValue == null) {
            return null
        }

        return args.action?.copy(icon = iconValue, iconRaw = icon, name = name, action = action)
            ?: WearAction(icon = iconValue, iconRaw = icon, name = name, action = action)
    }

    private fun findIcon(input: String): IconValue? {
        val processInput = input.toUpperCase(Locale.getDefault()).replace("-", "_")
        return IconValue.values().find { value -> value.name == processInput }
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