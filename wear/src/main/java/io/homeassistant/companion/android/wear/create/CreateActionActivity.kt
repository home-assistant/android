package io.homeassistant.companion.android.wear.create

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.wear.activity.ConfirmationActivity
import io.homeassistant.companion.android.common.actions.WearAction
import io.homeassistant.companion.android.wear.DaggerPresenterComponent
import io.homeassistant.companion.android.wear.PresenterModule
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.databinding.FragmentCreateActionBinding
import io.homeassistant.companion.android.wear.util.extensions.appComponent
import io.homeassistant.companion.android.wear.util.extensions.domainComponent
import io.homeassistant.companion.android.wear.util.extensions.viewBinding
import io.homeassistant.companion.android.wear.util.resources.actionIconById
import javax.inject.Inject

class CreateActionActivity : AppCompatActivity(), CreateActionView {

    @Inject lateinit var presenter: CreateActionPresenter

    private val binding by viewBinding(FragmentCreateActionBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        DaggerPresenterComponent.factory()
            .create(appComponent, domainComponent, PresenterModule(this), this)
            .inject(this)

        binding.actionIcon.setOnClickListener { showIconSelectAlertDialog() }
        binding.actionButton.setOnClickListener {
            val action = buildWearAction() ?: return@setOnClickListener
            presenter.createAction(action)
        }

        presenter.onViewReady()
    }

    private fun showIconSelectAlertDialog () {
        val adapter = IconAdapter(this)
        val dialog = AlertDialog.Builder(this)
            .setAdapter(adapter) { _, index ->
                val iconId = actionIconById(index)
                binding.actionIcon.setCompoundDrawablesWithIntrinsicBounds(iconId, 0, 0,0)
                binding.actionIcon.tag = index
            }
            .create()
        dialog.show()
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
        return WearAction(icon = icon, name = name, action = action)
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