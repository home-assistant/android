package io.homeassistant.companion.android.widgets.template

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.widgets.configure.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.configure.assistedWidgetViewModel
import io.homeassistant.companion.android.widgets.configure.widgetConfigureLauncherIntent

@AndroidEntryPoint
internal class TemplateWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        fun newInstance(context: Context): Intent =
            widgetConfigureLauncherIntent(context, TemplateWidgetConfigureActivity::class.java)
    }

    override val viewModel: TemplateWidgetConfigureViewModel by assistedWidgetViewModel<
        TemplateWidgetConfigureViewModel,
        TemplateWidgetConfigureViewModel.Factory,
        > { it.create(widgetId) }

    @Composable
    override fun ScreenContent(canNavigateBack: Boolean, onNavigate: () -> Unit, onActionClick: () -> Unit) {
        TemplateWidgetConfigureScreen(
            viewModel = viewModel,
            canNavigateBack = canNavigateBack,
            onNavigate = onNavigate,
            onActionClick = onActionClick,
        )
    }
}
