package io.homeassistant.companion.android.home.views.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRenderer
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import io.homeassistant.companion.android.tiles.dashboard.WearDashboardLayoutRules
import io.homeassistant.companion.android.tiles.dashboard.WearDashboardTileStateResolver
import io.homeassistant.companion.android.util.getIcon
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Root composable tree produced by [ComposeWearDashboardRenderer.render].
 */
data class WearDashboardComposeContent(val content: @Composable () -> Unit)

/**
 * Renders Wear Dashboard components into a Jetpack Compose tree for the full-screen app.
 */
@Singleton
class ComposeWearDashboardRenderer @Inject constructor(
    private val stateResolver: WearDashboardTileStateResolver,
) : WearDashboardRenderer<WearDashboardComposeContent> {

    override fun render(
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): WearDashboardComposeContent {
        val page = config.pages.firstOrNull { it.id == pageId } ?: config.pages.firstOrNull()
        return WearDashboardComposeContent {
            if (page == null) {
                Text("Missing page")
            } else {
                RenderScope(
                    stateResolver = stateResolver,
                    state = state,
                    capabilities = capabilities,
                    onAction = {},
                ).RenderComponent(component = page.root, depth = 0)
            }
        }
    }

    /**
     * Renders [config] with an [onAction] callback wired to interactive components.
     */
    fun renderInteractive(
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        onAction: (WearDashboardAction) -> Unit,
    ): WearDashboardComposeContent {
        val page = config.pages.firstOrNull { it.id == pageId } ?: config.pages.firstOrNull()
        return WearDashboardComposeContent {
            if (page == null) {
                Text("Missing page")
            } else {
                RenderScope(
                    stateResolver = stateResolver,
                    state = state,
                    capabilities = capabilities,
                    onAction = onAction,
                ).RenderComponent(component = page.root, depth = 0)
            }
        }
    }

    private class RenderScope(
        private val stateResolver: WearDashboardTileStateResolver,
        private val state: WearDashboardResolvedState,
        private val capabilities: WearDashboardCapabilities,
        private val onAction: (WearDashboardAction) -> Unit,
    ) {
        @Composable
        fun RenderComponent(component: WearDashboardComponent, depth: Int) {
            if (!WearDashboardLayoutRules.canDescend(depth, capabilities.maxComponentTreeDepth)) return

            component.visible?.let { visibleBinding ->
                if (!WearDashboardLayoutRules.isVisible(stateResolver.resolveString(visibleBinding, state))) {
                    return
                }
            }

            when (component) {
                is WearDashboardComponent.Text -> {
                    Text(stateResolver.resolveString(component.text, state))
                }
                is WearDashboardComponent.Icon -> {
                    DashboardIcon(stateResolver.resolveString(component.icon, state))
                }
                is WearDashboardComponent.Button -> {
                    val label = component.text?.let { stateResolver.resolveString(it, state) }.orEmpty()
                    val iconName = component.icon?.let { stateResolver.resolveString(it, state) }
                    androidx.wear.compose.material3.Button(
                        onClick = { component.tapAction?.let(onAction) },
                        label = { Text(label.ifEmpty { iconName.orEmpty() }) },
                    )
                }
                is WearDashboardComponent.StatusChip -> {
                    val stateText = stateResolver.resolveString(component.state, state)
                    val labelText = component.label?.let { stateResolver.resolveString(it, state) }
                    val chipText = listOfNotNull(labelText?.takeIf { it.isNotBlank() }, stateText)
                        .joinToString(": ")
                    androidx.wear.compose.material3.Button(
                        onClick = { component.tapAction?.let(onAction) },
                        label = { Text(chipText) },
                    )
                }
                is WearDashboardComponent.ProgressRing -> {
                    val rawValue = stateResolver.resolveString(component.value, state)
                    val numericValue = stateResolver.parseInt(rawValue, component.min)
                    val range = (component.max - component.min).coerceAtLeast(1)
                    val progress = ((numericValue - component.min).toFloat() / range).coerceIn(0f, 1f)
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress })
                    }
                }
                is WearDashboardComponent.Row -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        renderChildren(component.children, depth)
                    }
                }
                is WearDashboardComponent.Column -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        renderChildren(component.children, depth)
                    }
                }
                is WearDashboardComponent.Box -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        renderChildren(component.children, depth)
                    }
                }
                is WearDashboardComponent.Conditional -> {
                    val conditionValue = stateResolver.resolveString(component.condition, state)
                    val branch = if (stateResolver.isTruthy(conditionValue)) {
                        component.then
                    } else {
                        component.elseComponent
                    }
                    branch?.let { RenderComponent(it, depth + 1) }
                }
            }
        }

        @Composable
        private fun renderChildren(children: List<WearDashboardComponent>, depth: Int) {
            WearDashboardLayoutRules
                .limitChildren(children, capabilities.maxChildrenPerContainer)
                .forEach { child ->
                    RenderComponent(child, depth + 1)
                }
        }
    }
}

@Composable
private fun DashboardIcon(iconName: String) {
    val context = LocalContext.current
    Image(
        asset = getIcon(iconName, "sensor", context),
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(Color.White),
    )
}
