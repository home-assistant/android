package io.homeassistant.companion.android.widgets.configure

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import kotlinx.coroutines.launch

/** Intent extra carrying the entity a widget is preconfigured for, when launched from the app. */
private const val WIDGET_CONFIGURE_FOR_ENTITY = "for_entity"

/**
 * Builds the intent that opens a widget configuration [activityClass] from within the app, optionally
 * preconfigured for [entityId]. The launcher opens these activities through its own APPWIDGET_CONFIGURE
 * filter instead.
 */
internal fun widgetConfigureLauncherIntent(
    context: Context,
    activityClass: Class<*>,
    entityId: String? = null,
): Intent = Intent(context, activityClass).apply {
    entityId?.let { putExtra(WIDGET_CONFIGURE_FOR_ENTITY, it) }
    putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/**
 * Creates a configuration ViewModel through its Hilt assisted [F] factory, hiding the
 * creation-callback wiring. [create] turns the injected factory into the ViewModel.
 */
internal inline fun <reified VM : ViewModel, reified F> BaseWidgetConfigureActivity.assistedWidgetViewModel(
    crossinline create: (F) -> VM,
): Lazy<VM> = viewModels(
    extrasProducer = {
        defaultViewModelCreationExtras.withCreationCallback<F> { factory -> create(factory) }
    },
)

/**
 * Shared skeleton for the Compose widget configuration screens: it reads the widget id and launch mode
 * from the intent, renders [ScreenContent] inside [HATheme], and drives the create/update action
 * through the [viewModel]. Concrete activities only supply their [viewModel] and [ScreenContent].
 */
internal abstract class BaseWidgetConfigureActivity : BaseActivity() {

    /** The configuration ViewModel; concrete activities create it with their assisted factory. */
    protected abstract val viewModel: BaseWidgetConfigureViewModel<*, *>

    /** The widget id assigned by the launcher, or [AppWidgetManager.INVALID_APPWIDGET_ID] for a new one. */
    protected val widgetId: Int
        get() = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    /** Whether the app opened this screen (rather than the launcher), so it drives the pin request. */
    protected val requestLauncherSetup: Boolean
        get() = intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true

    /** The entity the widget is preconfigured for, when the app opened this screen for a specific one. */
    protected val preselectedEntityId: String?
        get() = intent.extras?.getString(WIDGET_CONFIGURE_FOR_ENTITY, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancelled by default so the launcher drops the placement if the user backs out.
        setResult(RESULT_CANCELED)

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        setContent {
            HATheme {
                ScreenContent(
                    // The app sets the launcher-setup extra when it opens this screen itself, so there
                    // is something to go back to; the launcher leaves nothing behind us.
                    canNavigateBack = requestLauncherSetup,
                    onNavigate = ::finish,
                    onActionClick = ::onActionClick,
                )
            }
        }
    }

    /** Renders the concrete widget's configuration screen. */
    @Composable
    protected abstract fun ScreenContent(canNavigateBack: Boolean, onNavigate: () -> Unit, onActionClick: () -> Unit)

    private fun onActionClick() {
        lifecycleScope.launch {
            if (requestLauncherSetup) {
                if (viewModel.requestWidgetCreation(this@BaseWidgetConfigureActivity)) {
                    finish()
                }
            } else if (viewModel.updateWidgetConfiguration(this@BaseWidgetConfigureActivity)) {
                viewModel.updateWidget(this@BaseWidgetConfigureActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }
    }
}
