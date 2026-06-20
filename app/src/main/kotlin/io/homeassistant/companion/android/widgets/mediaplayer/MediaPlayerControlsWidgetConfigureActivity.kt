package io.homeassistant.companion.android.widgets.mediaplayer

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureViewModel.Factory
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MediaPlayerControlsWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val FOR_ENTITY = "for_entity"

        fun newInstance(context: Context, entityId: String): Intent {
            return Intent(context, MediaPlayerControlsWidgetConfigureActivity::class.java).apply {
                putExtra(FOR_ENTITY, entityId)
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private val viewModel: MediaPlayerControlsWidgetConfigureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<Factory> { factory ->
                factory.create(intent.getStringExtra(FOR_ENTITY))
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

        val widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        viewModel.onSetup(widgetId)

        setContent {
            HATheme {
                MediaPlayerControlsWidgetConfigureScreen(
                    viewModel = viewModel,
                    dynamicColorAvailable = DynamicColors.isDynamicColorAvailable(),
                    onActionClick = ::onActionClick,
                    onClose = ::finish,
                )
            }
        }
    }

    private fun onActionClick() {
        lifecycleScope.launch {
            if (requestLauncherSetup) {
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O) && viewModel.isValidSelection()) {
                    requestPinWidget()
                } else {
                    viewModel.onUserMessage(commonR.string.widget_creation_error)
                }
            } else {
                updateWidget()
            }
        }
    }

    private suspend fun requestPinWidget() {
        try {
            viewModel.requestWidgetCreation(this)
            finish()
        } catch (e: IllegalStateException) {
            Timber.e(e, "Unable to request widget pin")
            viewModel.onUserMessage(commonR.string.widget_creation_error)
        }
    }

    private suspend fun updateWidget() {
        try {
            viewModel.updateWidgetConfiguration()
            viewModel.updateWidget(this)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, viewModel.widgetId),
            )
            finish()
        } catch (e: IllegalStateException) {
            Timber.e(e, "Unable to update widget")
            viewModel.onUserMessage(commonR.string.widget_update_error)
        }
    }
}
