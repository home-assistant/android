package io.homeassistant.companion.android.common.data.wear.dashboard.validation

import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardAction
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardComponent
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardRefreshPolicy
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardSchemaVersion

/** Maximum allowed depth of the component tree, including the root component. */
const val WEAR_DASHBOARD_MAX_COMPONENT_TREE_DEPTH = 5

/** Maximum number of children allowed in layout container components. */
const val WEAR_DASHBOARD_MAX_CHILDREN_PER_CONTAINER = 6

/** Minimum interval in seconds for periodic refresh policies. */
const val WEAR_DASHBOARD_MIN_REFRESH_INTERVAL_SECONDS = 1

private val ENTITY_ID_PATTERN = Regex("^[a-z][a-z0-9_]*\\.[a-z0-9_]+$")
private val IDENTIFIER_PATTERN = Regex("^[a-z][a-z0-9_]*$")

/**
 * A single validation issue found in a dashboard configuration.
 */
data class ValidationError(val path: String, val message: String)

/**
 * Result of validating a [WearDashboardConfig].
 */
data class ValidationResult(val errors: List<ValidationError>) {
    /** Whether the configuration passed all validation checks. */
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Validates a Wear Dashboard configuration and returns structured errors.
 */
fun validate(config: WearDashboardConfig): ValidationResult {
    val errors = mutableListOf<ValidationError>()
    validateConfig(config, errors)
    return ValidationResult(errors)
}

private fun validateConfig(config: WearDashboardConfig, errors: MutableList<ValidationError>) {
    if (config.id.isBlank()) {
        errors += ValidationError("id", "Dashboard id must not be blank")
    }
    if (!WearDashboardSchemaVersion.isSupported(config.version)) {
        errors += ValidationError(
            "version",
            "Unsupported schema version ${config.version}; supported: ${WearDashboardSchemaVersion.SUPPORTED_VERSIONS}",
        )
    }
    if (config.pages.isEmpty()) {
        errors += ValidationError("pages", "At least one page is required")
    }

    val pageIds = mutableSetOf<String>()
    config.pages.forEachIndexed { index, page ->
        val pagePath = "pages[$index]"
        if (page.id.isBlank()) {
            errors += ValidationError("$pagePath.id", "Page id must not be blank")
        } else if (!pageIds.add(page.id)) {
            errors += ValidationError("$pagePath.id", "Duplicate page id '${page.id}'")
        }
        validateComponent(page.root, "$pagePath.root", depth = 1, errors)
    }

    validateSurfaces(config, pageIds, errors)
    validateRefreshPolicy(config.refreshPolicy, "refreshPolicy", errors)
}

private fun validateSurfaces(config: WearDashboardConfig, pageIds: Set<String>, errors: MutableList<ValidationError>) {
    config.surfaces.tile?.page?.let { pageId ->
        if (pageId !in pageIds) {
            errors += ValidationError("surfaces.tile.page", "Unknown page id '$pageId'")
        }
    }
    config.surfaces.app?.startPage?.let { pageId ->
        if (pageId !in pageIds) {
            errors += ValidationError("surfaces.app.startPage", "Unknown page id '$pageId'")
        }
    }
    config.surfaces.ongoingActivity?.page?.let { pageId ->
        if (pageId !in pageIds) {
            errors += ValidationError("surfaces.ongoingActivity.page", "Unknown page id '$pageId'")
        }
    }
}

private fun validateRefreshPolicy(
    policy: WearDashboardRefreshPolicy,
    path: String,
    errors: MutableList<ValidationError>,
) {
    if (policy is WearDashboardRefreshPolicy.Interval && policy.seconds < WEAR_DASHBOARD_MIN_REFRESH_INTERVAL_SECONDS) {
        errors += ValidationError(
            "$path.seconds",
            "Refresh interval must be at least $WEAR_DASHBOARD_MIN_REFRESH_INTERVAL_SECONDS second",
        )
    }
}

private fun validateComponent(
    component: WearDashboardComponent,
    path: String,
    depth: Int,
    errors: MutableList<ValidationError>,
) {
    if (depth > WEAR_DASHBOARD_MAX_COMPONENT_TREE_DEPTH) {
        errors += ValidationError(
            path,
            "Component tree exceeds maximum depth of $WEAR_DASHBOARD_MAX_COMPONENT_TREE_DEPTH",
        )
        return
    }

    component.visible?.let { validateBinding(it, "$path.visible", errors) }
    component.tapAction?.let { validateAction(it, "$path.tapAction", errors) }
    component.holdAction?.let { validateAction(it, "$path.holdAction", errors) }

    when (component) {
        is WearDashboardComponent.Text -> validateBinding(component.text, "$path.text", errors)
        is WearDashboardComponent.Icon -> validateBinding(component.icon, "$path.icon", errors)
        is WearDashboardComponent.Button -> {
            component.text?.let { validateBinding(it, "$path.text", errors) }
            component.icon?.let { validateBinding(it, "$path.icon", errors) }
            if (component.text == null && component.icon == null) {
                errors += ValidationError(path, "Button must define text or icon")
            }
        }
        is WearDashboardComponent.StatusChip -> {
            validateBinding(component.state, "$path.state", errors)
            component.label?.let { validateBinding(it, "$path.label", errors) }
        }
        is WearDashboardComponent.ProgressRing -> {
            validateBinding(component.value, "$path.value", errors)
            if (component.min >= component.max) {
                errors += ValidationError(path, "Progress ring min must be less than max")
            }
        }
        is WearDashboardComponent.Row -> validateContainerChildren(component.children, path, depth, errors)
        is WearDashboardComponent.Column -> validateContainerChildren(component.children, path, depth, errors)
        is WearDashboardComponent.Box -> validateContainerChildren(component.children, path, depth, errors)
        is WearDashboardComponent.Conditional -> {
            validateBinding(component.condition, "$path.condition", errors)
            validateComponent(component.then, "$path.then", depth + 1, errors)
            component.elseComponent?.let { validateComponent(it, "$path.else", depth + 1, errors) }
        }
    }
}

private fun validateContainerChildren(
    children: List<WearDashboardComponent>,
    path: String,
    depth: Int,
    errors: MutableList<ValidationError>,
) {
    if (children.size > WEAR_DASHBOARD_MAX_CHILDREN_PER_CONTAINER) {
        errors += ValidationError(
            "$path.children",
            "Container exceeds maximum of $WEAR_DASHBOARD_MAX_CHILDREN_PER_CONTAINER children",
        )
    }
    children.forEachIndexed { index, child ->
        validateComponent(child, "$path.children[$index]", depth + 1, errors)
    }
}

private fun validateBinding(binding: WearDashboardBinding, path: String, errors: MutableList<ValidationError>) {
    when (binding) {
        is WearDashboardBinding.EntityState -> validateEntityId(binding.entityId, path, errors)
        is WearDashboardBinding.Template -> {
            if (binding.template.isBlank()) {
                errors += ValidationError(path, "Template must not be blank")
            }
        }
        is WearDashboardBinding.Constant -> Unit
    }
}

private fun validateAction(action: WearDashboardAction, path: String, errors: MutableList<ValidationError>) {
    when (action) {
        is WearDashboardAction.ToggleEntity -> validateEntityId(action.entityId, path, errors)
        is WearDashboardAction.CallService -> {
            validateIdentifier(action.domain, "$path.domain", "domain", errors)
            validateIdentifier(action.service, "$path.service", "service", errors)
        }
        is WearDashboardAction.Navigate -> {
            if (action.dashboardId.isBlank()) {
                errors += ValidationError("$path.dashboardId", "Dashboard id must not be blank")
            }
            if (action.pageId.isBlank()) {
                errors += ValidationError("$path.pageId", "Page id must not be blank")
            }
        }
        is WearDashboardAction.OpenFullDashboard -> {
            if (action.dashboardId.isBlank()) {
                errors += ValidationError("$path.dashboardId", "Dashboard id must not be blank")
            }
        }
        is WearDashboardAction.Refresh -> Unit
    }
}

private fun validateEntityId(entityId: String, path: String, errors: MutableList<ValidationError>) {
    if (entityId.isBlank()) {
        errors += ValidationError(path, "Entity id must not be blank")
    } else if (!ENTITY_ID_PATTERN.matches(entityId)) {
        errors += ValidationError(path, "Invalid entity id '$entityId'")
    }
}

private fun validateIdentifier(value: String, path: String, label: String, errors: MutableList<ValidationError>) {
    if (value.isBlank()) {
        errors += ValidationError(path, "$label must not be blank")
    } else if (!IDENTIFIER_PATTERN.matches(value)) {
        errors += ValidationError(path, "Invalid $label '$value'")
    }
}
