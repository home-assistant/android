package io.homeassistant.companion.android.tiles.dashboard

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRenderer
import io.homeassistant.companion.android.common.data.wear.dashboard.state.WearDashboardResolvedState
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardCapabilities
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TEXT_SIZE_SP = 14f
private const val BUTTON_SIZE_DP = 48f
private const val ICON_SIZE_DP = 24f
private const val SPACING_DP = 4f
private const val PROGRESS_RING_THICKNESS_DP = 4f

/**
 * Renders Wear Dashboard components into ProtoLayout [LayoutElement] trees for tiles.
 */
@Singleton
class ProtoLayoutWearDashboardRenderer @Inject constructor(
    private val stateResolver: WearDashboardTileStateResolver,
    private val actionSerializer: WearDashboardActionSerializer,
    private val dynamicExpressionRenderer: WearDashboardDynamicExpressionRenderer,
) : WearDashboardRenderer<LayoutElement> {

    /** Icon resource IDs referenced by the latest render pass. */
    val iconResourceIds: MutableSet<String> = linkedSetOf()

    /** Device parameters from the active tile request. */
    var deviceParameters: DeviceParameters? = null

    override fun render(
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): LayoutElement {
        actionSerializer.clear()
        iconResourceIds.clear()
        dynamicExpressionRenderer.beginRenderPass(capabilities.maxDynamicExpressions)

        val page = config.pages.firstOrNull { it.id == pageId }
            ?: config.pages.firstOrNull()

        if (page == null) {
            return emptyStateText("Missing page")
        }

        val context = applicationContext ?: return emptyStateText("Missing context")
        val deviceParams = deviceParameters ?: return emptyStateText("Missing device parameters")

        return renderComponent(
            context = context,
            deviceParams = deviceParams,
            component = page.root,
            state = state,
            capabilities = capabilities,
            depth = 0,
        ) ?: emptyStateText("Unsupported layout")
    }

    private var applicationContext: Context? = null

    /**
     * Renders a dashboard page using Android [context] and [deviceParams].
     */
    fun renderWithContext(
        context: Context,
        deviceParams: DeviceParameters,
        config: WearDashboardConfig,
        pageId: String,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): LayoutElement {
        applicationContext = context.applicationContext
        deviceParameters = deviceParams
        return render(config, pageId, state, capabilities)
    }

    private fun renderComponent(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        depth: Int,
    ): LayoutElement? {
        if (!WearDashboardLayoutRules.canDescend(depth, capabilities.maxComponentTreeDepth)) {
            return null
        }

        component.visible?.let { visibleBinding ->
            val visibleValue = stateResolver.resolveString(visibleBinding, state)
            if (!WearDashboardLayoutRules.isVisible(visibleValue)) {
                return null
            }
        }

        return when (component) {
            is WearDashboardComponent.Text -> renderText(component, state)
            is WearDashboardComponent.Icon -> renderIcon(component, state)
            is WearDashboardComponent.Button -> renderButton(context, deviceParams, component, state)
            is WearDashboardComponent.StatusChip -> renderStatusChip(context, deviceParams, component, state)
            is WearDashboardComponent.ProgressRing -> renderProgressRing(component, state, capabilities)
            is WearDashboardComponent.Row -> renderRow(context, deviceParams, component, state, capabilities, depth)
            is WearDashboardComponent.Column -> renderColumn(context, deviceParams, component, state, capabilities, depth)
            is WearDashboardComponent.Box -> renderBox(context, deviceParams, component, state, capabilities, depth)
            is WearDashboardComponent.Conditional -> renderConditional(
                context,
                deviceParams,
                component,
                state,
                capabilities,
                depth,
            )
        }?.let { element ->
            applyComponentActions(element, component)
        }
    }

    private fun renderText(component: WearDashboardComponent.Text, state: WearDashboardResolvedState): LayoutElement {
        val text = stateResolver.resolveString(component.text, state)
        return LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setMaxLines(4)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(DEFAULT_TEXT_SIZE_SP))
                    .build(),
            )
            .build()
    }

    private fun renderIcon(component: WearDashboardComponent.Icon, state: WearDashboardResolvedState): LayoutElement {
        val iconName = stateResolver.resolveString(component.icon, state)
        val resourceId = iconResourceId(component.id, iconName)
        iconResourceIds.add(resourceId)
        return LayoutElementBuilders.Image.Builder()
            .setResourceId(resourceId)
            .setWidth(DimensionBuilders.dp(ICON_SIZE_DP))
            .setHeight(DimensionBuilders.dp(ICON_SIZE_DP))
            .build()
    }

    private fun renderButton(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent.Button,
        state: WearDashboardResolvedState,
    ): LayoutElement {
        val label = component.text?.let { stateResolver.resolveString(it, state) }.orEmpty()
        val iconName = component.icon?.let { stateResolver.resolveString(it, state) }

        if (iconName != null) {
            val resourceId = iconResourceId(component.id, iconName)
            iconResourceIds.add(resourceId)
            return LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.dp(BUTTON_SIZE_DP))
                .setHeight(DimensionBuilders.dp(BUTTON_SIZE_DP))
                .setModifiers(
                    clickableModifiers(component.tapAction, component.id),
                )
                .addContent(
                    LayoutElementBuilders.Image.Builder()
                        .setResourceId(resourceId)
                        .setWidth(DimensionBuilders.dp(ICON_SIZE_DP))
                        .setHeight(DimensionBuilders.dp(ICON_SIZE_DP))
                        .build(),
                )
                .build()
        }

        val theme = materialColors(context)
        val clickable = component.tapAction?.let { action ->
            ModifiersBuilders.Clickable.Builder()
                .setId(actionSerializer.registerAction(action, component.id))
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build()
        }

        if (clickable != null) {
            return CompactChip.Builder(
                context,
                label.ifEmpty { " " },
                clickable,
                deviceParams,
            )
                .setChipColors(ChipColors.primaryChipColors(theme))
                .build()
        }

        return LayoutElementBuilders.Text.Builder()
            .setText(label)
            .setMaxLines(2)
            .build()
    }

    private fun renderStatusChip(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent.StatusChip,
        state: WearDashboardResolvedState,
    ): LayoutElement {
        val stateText = stateResolver.resolveString(component.state, state)
        val labelText = component.label?.let { stateResolver.resolveString(it, state) }
        val chipText = listOfNotNull(labelText?.takeIf { it.isNotBlank() }, stateText)
            .joinToString(": ")

        val clickable = component.tapAction?.let { action ->
            ModifiersBuilders.Clickable.Builder()
                .setId(actionSerializer.registerAction(action, component.id))
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build()
        } ?: ModifiersBuilders.Clickable.Builder()
            .setId("status_chip")
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        return CompactChip.Builder(
            context,
            chipText.ifEmpty { " " },
            clickable,
            deviceParams,
        )
            .setChipColors(ChipColors.primaryChipColors(materialColors(context)))
            .build()
    }

    private fun renderProgressRing(
        component: WearDashboardComponent.ProgressRing,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
    ): LayoutElement {
        val rawValue = stateResolver.resolveString(component.value, state)
        val numericValue = stateResolver.parseInt(rawValue, component.min)
        val range = (component.max - component.min).coerceAtLeast(1)
        val progress = ((numericValue - component.min).toFloat() / range).coerceIn(0f, 1f)

        if (capabilities.supportsDynamicExpressions) {
            dynamicExpressionRenderer.renderProgressRing(progress)?.let { return it }
        }

        val sweepDegrees = progress * 360f
        return LayoutElementBuilders.Arc.Builder()
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.DegreesProp.Builder(sweepDegrees).build())
                    .setThickness(DimensionBuilders.DpProp.Builder(PROGRESS_RING_THICKNESS_DP).build())
                    .setColor(ColorBuilders.argb(0xFF03DAC5.toInt()))
                    .build(),
            )
            .build()
    }

    private fun renderRow(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent.Row,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        depth: Int,
    ): LayoutElement {
        val children = renderChildComponents(
            context,
            deviceParams,
            WearDashboardLayoutRules.limitChildren(component.children, capabilities.maxChildrenPerContainer),
            state,
            capabilities,
            depth + 1,
        )
        return LayoutElementBuilders.Row.Builder().apply {
            children.forEachIndexed { index, child ->
                if (index > 0) {
                    addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setWidth(DimensionBuilders.dp(SPACING_DP))
                            .build(),
                    )
                }
                addContent(child)
            }
        }.build()
    }

    private fun renderColumn(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent.Column,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        depth: Int,
    ): LayoutElement {
        val children = renderChildComponents(
            context,
            deviceParams,
            WearDashboardLayoutRules.limitChildren(component.children, capabilities.maxChildrenPerContainer),
            state,
            capabilities,
            depth + 1,
        )
        return LayoutElementBuilders.Column.Builder().apply {
            children.forEachIndexed { index, child ->
                if (index > 0) {
                    addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(SPACING_DP))
                            .build(),
                    )
                }
                addContent(child)
            }
        }.build()
    }

    private fun renderBox(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent.Box,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        depth: Int,
    ): LayoutElement {
        val children = renderChildComponents(
            context,
            deviceParams,
            WearDashboardLayoutRules.limitChildren(component.children, capabilities.maxChildrenPerContainer),
            state,
            capabilities,
            depth + 1,
        )
        return LayoutElementBuilders.Box.Builder().apply {
            children.forEach { addContent(it) }
        }.build()
    }

    private fun renderConditional(
        context: Context,
        deviceParams: DeviceParameters,
        component: WearDashboardComponent.Conditional,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        depth: Int,
    ): LayoutElement? {
        val conditionValue = stateResolver.resolveString(component.condition, state)
        val branch = if (stateResolver.isTruthy(conditionValue)) {
            component.then
        } else {
            component.elseComponent
        }
        return branch?.let {
            renderComponent(context, deviceParams, it, state, capabilities, depth + 1)
        }
    }

    private fun renderChildComponents(
        context: Context,
        deviceParams: DeviceParameters,
        children: List<WearDashboardComponent>,
        state: WearDashboardResolvedState,
        capabilities: WearDashboardCapabilities,
        depth: Int,
    ): List<LayoutElement> = children.mapNotNull { child ->
        renderComponent(context, deviceParams, child, state, capabilities, depth)
    }

    private fun applyComponentActions(
        element: LayoutElement,
        component: WearDashboardComponent,
    ): LayoutElement {
        if (component is WearDashboardComponent.Button) {
            return element
        }
        val tapAction = component.tapAction ?: return element
        val clickableId = actionSerializer.registerAction(tapAction, component.id)
        return LayoutElementBuilders.Box.Builder()
            .addContent(element)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(clickableId)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun clickableModifiers(action: WearDashboardAction?, componentId: String?): ModifiersBuilders.Modifiers {
        if (action == null) {
            return ModifiersBuilders.Modifiers.Builder().build()
        }
        return ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(actionSerializer.registerAction(action, componentId))
                    .setOnClick(ActionBuilders.LoadAction.Builder().build())
                    .build(),
            )
            .build()
    }

    private fun iconResourceId(componentId: String?, iconName: String): String =
        "icon:${componentId.orEmpty()}:$iconName"

    private fun materialColors(context: Context): Colors = Colors(
        ContextCompat.getColor(context, commonR.color.colorPrimary),
        ContextCompat.getColor(context, commonR.color.colorOnPrimary),
        ContextCompat.getColor(context, R.color.colorOverlay),
        ContextCompat.getColor(context, android.R.color.white),
    )

    private fun emptyStateText(message: String): LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(message)
            .setMaxLines(3)
            .build()
}
