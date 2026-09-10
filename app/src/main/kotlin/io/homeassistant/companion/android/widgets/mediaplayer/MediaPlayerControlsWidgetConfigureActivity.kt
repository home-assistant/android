package io.homeassistant.companion.android.widgets.mediaplayer

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
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureViewModel.Factory
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MediaPlayerControlsWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val FOR_ENTITY = "for_entity"

        fun newInstance(context: Context, entityId: String? = null): Intent {
            return Intent(context, MediaPlayerControlsWidgetConfigureActivity::class.java).apply {
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

    private val viewModel: MediaPlayerControlsWidgetConfigureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<Factory> { factory ->
                factory.create(widgetId, intent.extras?.getString(FOR_ENTITY, null))
            }
        },
    )

    private val requestLauncherSetup: Boolean
        get() = intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED. This will cause the widget host to cancel out of the widget
        // placement if the user closes the screen or presses the back button.
        setResult(RESULT_CANCELED)

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        setContent {
            HATheme {
                MediaPlayerControlsWidgetConfigureScreen(
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
                if (viewModel.requestWidgetCreation(this@MediaPlayerControlsWidgetConfigureActivity)) {
                    finish()
                }
            } else {
                if (viewModel.updateWidgetConfiguration()) {
                    viewModel.updateWidget(this@MediaPlayerControlsWidgetConfigureActivity)
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
