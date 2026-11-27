package io.homeassistant.companion.android.widgets.button

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.widgets.configure.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.configure.assistedWidgetViewModel
import io.homeassistant.companion.android.widgets.configure.widgetConfigureLauncherIntent

@AndroidEntryPoint
internal class ButtonWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        fun newInstance(context: Context, entityId: String? = null): Intent =
            widgetConfigureLauncherIntent(context, ButtonWidgetConfigureActivity::class.java, entityId)
    }

    override val viewModel: ButtonWidgetViewModel by assistedWidgetViewModel<
        ButtonWidgetViewModel,
        ButtonWidgetViewModel.Factory,
        > { it.create(widgetId, preselectedEntityId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent(canNavigateBack: Boolean, onNavigate: () -> Unit, onActionClick: () -> Unit) {
        ButtonWidgetConfigureScreen(
            viewModel = viewModel,
            canNavigateBack = canNavigateBack,
            onNavigate = onNavigate,
            onActionClick = onActionClick,
        )
    }
}
