package io.homeassistant.companion.android.widgets.todo

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.widgets.configure.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.configure.assistedWidgetViewModel
import io.homeassistant.companion.android.widgets.configure.widgetConfigureLauncherIntent

@AndroidEntryPoint
internal class TodoWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        fun newInstance(context: Context, entityId: String? = null): Intent =
            widgetConfigureLauncherIntent(context, TodoWidgetConfigureActivity::class.java, entityId)
    }

    override val viewModel: TodoWidgetConfigureViewModel by assistedWidgetViewModel<
        TodoWidgetConfigureViewModel,
        TodoWidgetConfigureViewModel.Factory,
        > { it.create(widgetId, preselectedEntityId) }

    @Composable
    override fun ScreenContent(canNavigateBack: Boolean, onNavigate: () -> Unit, onActionClick: () -> Unit) {
        TodoWidgetConfigureScreen(
            viewModel = viewModel,
            canNavigateBack = canNavigateBack,
            onNavigate = onNavigate,
            onActionClick = onActionClick,
        )
    }
}
