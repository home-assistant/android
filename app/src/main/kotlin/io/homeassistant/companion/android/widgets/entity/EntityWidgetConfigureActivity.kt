package io.homeassistant.companion.android.widgets.entity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.util.getHexForColor
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EntityWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val FOR_ENTITY = "for_entity"

        fun newInstance(context: Context, entityId: String): Intent {
            return Intent(context, EntityWidgetConfigureActivity::class.java).apply {
                putExtra(FOR_ENTITY, entityId)
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private val viewModel: EntityWidgetConfigureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<EntityWidgetConfigureViewModel.Factory> { factory ->
                factory.create(intent.extras?.getString(FOR_ENTITY, null))
            }
        },
    )

    private val requestLauncherSetup: Boolean
        get() = intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeCompat()
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        val widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        viewModel.onSetup(
            widgetId = widgetId,
            defaultBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
                WidgetBackgroundType.DYNAMICCOLOR
            } else {
                WidgetBackgroundType.DAYNIGHT
            },
            textColors = EntityWidgetTextColors(
                white = application.getHexForColor(android.R.color.white),
                black = application.getHexForColor(commonR.color.colorWidgetButtonLabelBlack),
            ),
        )

        setContent {
            HATheme {
                EntityWidgetConfigureScreen(
                    viewModel = viewModel,
                    dynamicColorAvailable = DynamicColors.isDynamicColorAvailable(),
                    onActionClick = ::onActionClick,
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
                    showWidgetError(commonR.string.widget_creation_error)
                }
            } else {
                updateWidget()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun requestPinWidget() {
        try {
            viewModel.requestWidgetCreation(this)
            finish()
        } catch (_: IllegalStateException) {
            showWidgetError(commonR.string.widget_creation_error)
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
        } catch (_: IllegalStateException) {
            showWidgetError(commonR.string.widget_update_error)
        }
    }

    private fun showWidgetError(message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
