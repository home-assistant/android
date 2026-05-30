package io.homeassistant.companion.android.common.data.wear.dashboard.state

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * A binding reference discovered while walking a dashboard component tree.
 */
data class WearDashboardBindingDependency(
    val path: String,
    val binding: WearDashboardBinding,
)

/**
 * Dependencies required to resolve runtime state for a dashboard configuration.
 */
data class WearDashboardDependencies(
    val entityIds: Set<String>,
    val templates: Set<String>,
    val bindings: List<WearDashboardBindingDependency>,
)

/**
 * Extracts entity IDs, templates, and binding paths from a dashboard configuration.
 */
object WearDashboardDependencyExtractor {

    /**
     * Finds all entity IDs, templates, and binding paths referenced by [config].
     */
    fun extract(config: WearDashboardConfig): WearDashboardDependencies {
        val entityIds = linkedSetOf<String>()
        val templates = linkedSetOf<String>()
        val bindings = mutableListOf<WearDashboardBindingDependency>()

        config.pages.forEach { page ->
            collectComponentDependencies(
                component = page.root,
                componentPath = page.root.id ?: page.id,
                entityIds = entityIds,
                templates = templates,
                bindings = bindings,
            )
        }

        return WearDashboardDependencies(
            entityIds = entityIds,
            templates = templates,
            bindings = bindings,
        )
    }

    private fun collectComponentDependencies(
        component: WearDashboardComponent,
        componentPath: String,
        entityIds: MutableSet<String>,
        templates: MutableSet<String>,
        bindings: MutableList<WearDashboardBindingDependency>,
    ) {
        collectBinding(component.visible, "$componentPath.visible", entityIds, templates, bindings)
        collectActionDependencies(component.tapAction, entityIds)
        collectActionDependencies(component.holdAction, entityIds)

        when (component) {
            is WearDashboardComponent.Text -> {
                collectBinding(component.text, "$componentPath.text", entityIds, templates, bindings)
            }

            is WearDashboardComponent.Icon -> {
                collectBinding(component.icon, "$componentPath.icon", entityIds, templates, bindings)
            }

            is WearDashboardComponent.Button -> {
                component.text?.let {
                    collectBinding(it, "$componentPath.text", entityIds, templates, bindings)
                }
                component.icon?.let {
                    collectBinding(it, "$componentPath.icon", entityIds, templates, bindings)
                }
            }

            is WearDashboardComponent.StatusChip -> {
                collectBinding(component.state, "$componentPath.state", entityIds, templates, bindings)
                component.label?.let {
                    collectBinding(it, "$componentPath.label", entityIds, templates, bindings)
                }
            }

            is WearDashboardComponent.ProgressRing -> {
                collectBinding(component.value, "$componentPath.value", entityIds, templates, bindings)
            }

            is WearDashboardComponent.Row -> {
                component.children.forEachIndexed { index, child ->
                    collectComponentDependencies(
                        component = child,
                        componentPath = childPath(componentPath, child, index),
                        entityIds = entityIds,
                        templates = templates,
                        bindings = bindings,
                    )
                }
            }

            is WearDashboardComponent.Column -> {
                component.children.forEachIndexed { index, child ->
                    collectComponentDependencies(
                        component = child,
                        componentPath = childPath(componentPath, child, index),
                        entityIds = entityIds,
                        templates = templates,
                        bindings = bindings,
                    )
                }
            }

            is WearDashboardComponent.Box -> {
                component.children.forEachIndexed { index, child ->
                    collectComponentDependencies(
                        component = child,
                        componentPath = childPath(componentPath, child, index),
                        entityIds = entityIds,
                        templates = templates,
                        bindings = bindings,
                    )
                }
            }

            is WearDashboardComponent.Conditional -> {
                collectBinding(
                    component.condition,
                    "$componentPath.condition",
                    entityIds,
                    templates,
                    bindings,
                )
                collectComponentDependencies(
                    component = component.then,
                    componentPath = childPath(componentPath, component.then, 0),
                    entityIds = entityIds,
                    templates = templates,
                    bindings = bindings,
                )
                component.elseComponent?.let { elseComponent ->
                    collectComponentDependencies(
                        component = elseComponent,
                        componentPath = childPath(componentPath, elseComponent, 1),
                        entityIds = entityIds,
                        templates = templates,
                        bindings = bindings,
                    )
                }
            }
        }
    }

    private fun childPath(parentPath: String, child: WearDashboardComponent, index: Int): String {
        return child.id ?: "$parentPath.$index"
    }

    private fun collectBinding(
        binding: WearDashboardBinding?,
        path: String,
        entityIds: MutableSet<String>,
        templates: MutableSet<String>,
        bindings: MutableList<WearDashboardBindingDependency>,
    ) {
        if (binding == null) return
        bindings += WearDashboardBindingDependency(path = path, binding = binding)
        when (binding) {
            is WearDashboardBinding.EntityState -> entityIds += binding.entityId
            is WearDashboardBinding.Template -> templates += binding.template
            is WearDashboardBinding.Constant -> Unit
        }
    }

    private fun collectActionDependencies(action: WearDashboardAction?, entityIds: MutableSet<String>) {
        when (action) {
            is WearDashboardAction.ToggleEntity -> entityIds += action.entityId
            is WearDashboardAction.CallService -> {
                action.data["entity_id"]?.let { entityIdElement ->
                    entityIdFromJson(entityIdElement)?.let { entityIds += it }
                }
            }
            is WearDashboardAction.Navigate,
            is WearDashboardAction.Refresh,
            is WearDashboardAction.OpenFullDashboard,
            null,
            -> Unit
        }
    }

    private fun entityIdFromJson(element: JsonElement): String? {
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
