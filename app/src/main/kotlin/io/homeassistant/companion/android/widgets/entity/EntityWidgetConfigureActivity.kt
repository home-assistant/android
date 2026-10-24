package io.homeassistant.companion.android.widgets.entity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EntityWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val FOR_ENTITY = "for_entity"

        fun newInstance(context: Context, entityId: String? = null): Intent {
            return Intent(context, EntityWidgetConfigureActivity::class.java).apply {
                entityId?.let { putExtra(FOR_ENTITY, it) }
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private val widgetId: Int
        get() = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    private val viewModel: EntityWidgetConfigureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<EntityWidgetConfigureViewModel.Factory> { factory ->
                factory.create(widgetId, intent.extras?.getString(FOR_ENTITY, null))
            }
        },
    )

    private val requestLauncherSetup: Boolean
        get() = intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        setContent {
            HATheme {
                EntityWidgetConfigureScreen(
                    viewModel = viewModel,
                    // The app sets the extra when it opens this screen itself, so there is
                    // something to go back to. The launcher opens it through the
                    // APPWIDGET_CONFIGURE filter instead, leaving nothing behind us.
                    canNavigateBack = requestLauncherSetup,
                    onNavigate = ::finish,
                    onActionClick = ::onActionClick,
                )
            }
        }
    }

    private fun onActionClick() {
        lifecycleScope.launch {
            if (requestLauncherSetup) {
                if (viewModel.requestWidgetCreation(this@EntityWidgetConfigureActivity)) {
                    finish()
                }
            } else {
                if (viewModel.updateWidgetConfiguration()) {
                    viewModel.updateWidget(this@EntityWidgetConfigureActivity)
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                    )
                    finish()
                }
            }
        }
    }
}
