package io.homeassistant.companion.android.common.data.wear.dashboard.state

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardBindingKey
import io.homeassistant.companion.android.common.data.wear.dashboard.WearDashboardRepository
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardBinding
import io.homeassistant.companion.android.common.data.wear.dashboard.model.WearDashboardConfig
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

@OptIn(ExperimentalTime::class)
internal class WearDashboardUpdateCoordinatorImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val dashboardRepository: WearDashboardRepository,
    private val stateCache: WearDashboardStateCache,
    private val clock: Clock = Clock.System,
) : WearDashboardUpdateCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trackingJobs = mutableMapOf<String, Job>()
    private val jobsMutex = Mutex()
    private val entityStates = mutableMapOf<String, MutableMap<String, Entity>>()
    private val templateValues = mutableMapOf<String, MutableMap<String, String?>>()
    private val stateMutex = Mutex()
    private val lastTileUpdateRequest = mutableMapOf<String, Instant>()
    private val tileUpdateThrottle = 2.seconds

    private val _tileUpdateRequests = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val tileUpdateRequests = _tileUpdateRequests.asSharedFlow()

    override fun startTracking(dashboardId: String) {
        scope.launch {
            jobsMutex.withLock {
                trackingJobs.remove(dashboardId)?.cancel()
                trackingJobs[dashboardId] = scope.launch {
                    trackDashboard(dashboardId)
                }
            }
        }
    }

    override fun stopTracking(dashboardId: String) {
        scope.launch {
            jobsMutex.withLock {
                trackingJobs.remove(dashboardId)?.cancel()
            }
            stateMutex.withLock {
                entityStates.remove(dashboardId)
                templateValues.remove(dashboardId)
                lastTileUpdateRequest.remove(dashboardId)
            }
        }
    }

    override fun stopAll() {
        scope.launch {
            jobsMutex.withLock {
                trackingJobs.values.forEach { it.cancel() }
                trackingJobs.clear()
            }
            stateMutex.withLock {
                entityStates.clear()
                templateValues.clear()
                lastTileUpdateRequest.clear()
            }
        }
    }

    override suspend fun refreshNow(dashboardId: String) {
        val config = dashboardRepository.getDashboard(dashboardId) ?: return
        val integrationRepository = integrationRepositoryOrNull() ?: run {
            markStale(dashboardId)
            return
        }
        refreshState(
            dashboardId = dashboardId,
            config = config,
            integrationRepository = integrationRepository,
            requestTileUpdate = true,
        )
    }

    private suspend fun trackDashboard(dashboardId: String) {
        val config = dashboardRepository.getDashboard(dashboardId)
        if (config == null) {
            Timber.w("Cannot track missing wear dashboard id=$dashboardId")
            return
        }

        val dependencies = WearDashboardDependencyExtractor.extract(config)
        val integrationRepository = integrationRepositoryOrNull()
        if (integrationRepository == null) {
            markStale(dashboardId)
            return
        }

        refreshState(
            dashboardId = dashboardId,
            config = config,
            integrationRepository = integrationRepository,
            requestTileUpdate = false,
        )

        val entityIds = dependencies.entityIds.toList()
        if (entityIds.isNotEmpty()) {
            val entityUpdates = integrationRepository.getEntityUpdates(entityIds)
            if (entityUpdates != null) {
                try {
                    entityUpdates.collect { entity ->
                        updateEntityState(dashboardId, config, entity)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Wear dashboard entity subscription failed for id=$dashboardId")
                    markStale(dashboardId)
                }
            } else {
                markStale(dashboardId)
            }
        }

        dependencies.templates.forEach { template ->
            val templateUpdates = integrationRepository.getTemplateUpdates(template)
            if (templateUpdates == null) {
                markStale(dashboardId)
                return@forEach
            }
            scope.launch {
                try {
                    templateUpdates.collect { rendered ->
                        updateTemplateValue(dashboardId, config, template, rendered)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Wear dashboard template subscription failed for id=$dashboardId")
                    markStale(dashboardId)
                }
            }
        }
    }

    private suspend fun integrationRepositoryOrNull(): IntegrationRepository? {
        return try {
            if (!serverManager.isRegistered()) return null
            serverManager.integrationRepository()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Timber.w(e, "No integration repository available for wear dashboard updates")
            null
        }
    }

    private suspend fun updateEntityState(
        dashboardId: String,
        config: WearDashboardConfig,
        entity: Entity,
    ) {
        stateMutex.withLock {
            entityStates.getOrPut(dashboardId) { mutableMapOf() }[entity.entityId] = entity
        }
        publishResolvedState(dashboardId, config, isStale = false, requestTileUpdate = true)
    }

    private suspend fun updateTemplateValue(
        dashboardId: String,
        config: WearDashboardConfig,
        template: String,
        rendered: String?,
    ) {
        stateMutex.withLock {
            templateValues.getOrPut(dashboardId) { mutableMapOf() }[template] = rendered
        }
        publishResolvedState(dashboardId, config, isStale = false, requestTileUpdate = true)
    }

    private suspend fun refreshState(
        dashboardId: String,
        config: WearDashboardConfig,
        integrationRepository: IntegrationRepository,
        requestTileUpdate: Boolean,
    ) {
        val dependencies = WearDashboardDependencyExtractor.extract(config)
        val entitiesById = integrationRepository.getEntities()
            ?.associateBy { it.entityId }
            .orEmpty()

        stateMutex.withLock {
            val entityMap = entityStates.getOrPut(dashboardId) { mutableMapOf() }
            dependencies.entityIds.forEach { entityId ->
                entitiesById[entityId]?.let { entityMap[entityId] = it }
            }

            val templateMap = templateValues.getOrPut(dashboardId) { mutableMapOf() }
            dependencies.templates.forEach { template ->
                if (template !in templateMap) {
                    templateMap[template] = integrationRepository.renderTemplate(template, emptyMap())
                }
            }
        }

        publishResolvedState(
            dashboardId = dashboardId,
            config = config,
            isStale = false,
            requestTileUpdate = requestTileUpdate,
        )
    }

    private suspend fun publishResolvedState(
        dashboardId: String,
        config: WearDashboardConfig,
        isStale: Boolean,
        requestTileUpdate: Boolean,
    ) {
        val dependencies = WearDashboardDependencyExtractor.extract(config)
        val (entities, templates) = stateMutex.withLock {
            entityStates[dashboardId].orEmpty() to templateValues[dashboardId].orEmpty()
        }

        val values = buildMap {
            dependencies.bindings.forEach { dependency ->
                val key = WearDashboardBindingKey.keyFor(dependency.binding) ?: return@forEach
                put(
                    key,
                    resolveBinding(
                        binding = dependency.binding,
                        entities = entities,
                        templates = templates,
                    ),
                )
            }
        }

        stateCache.updateState(
            dashboardId = dashboardId,
            state = WearDashboardResolvedState(
                values = values,
                isStale = isStale,
                lastUpdated = clock.now(),
            ),
        )

        if (requestTileUpdate) {
            requestTileUpdateIfAllowed(dashboardId)
        }
    }

    private suspend fun markStale(dashboardId: String) {
        val current = stateCache.getState(dashboardId)
        stateCache.updateState(
            dashboardId = dashboardId,
            state = (current ?: WearDashboardResolvedState()).copy(isStale = true),
        )
    }

    private suspend fun requestTileUpdateIfAllowed(dashboardId: String) {
        val now = clock.now()
        val shouldEmit = stateMutex.withLock {
            val lastRequest = lastTileUpdateRequest[dashboardId]
            if (lastRequest == null || now - lastRequest >= tileUpdateThrottle) {
                lastTileUpdateRequest[dashboardId] = now
                true
            } else {
                false
            }
        }
        if (shouldEmit) {
            _tileUpdateRequests.tryEmit(dashboardId)
        }
    }

    private fun resolveBinding(
        binding: WearDashboardBinding,
        entities: Map<String, Entity>,
        templates: Map<String, String?>,
    ): ResolvedComponentValue {
        return when (binding) {
            is WearDashboardBinding.Constant -> resolveConstant(binding.value)
            is WearDashboardBinding.EntityState -> {
                val entity = entities[binding.entityId]
                if (entity == null) {
                    ResolvedComponentValue.Unknown(null)
                } else {
                    val raw = binding.attribute?.let { attribute ->
                        entity.attributes[attribute]?.toString()
                    } ?: entity.state
                    resolveRawValue(raw)
                }
            }
            is WearDashboardBinding.Template -> {
                resolveRawValue(templates[binding.template])
            }
        }
    }

    private fun resolveConstant(value: JsonElement): ResolvedComponentValue {
        return when (value) {
            is JsonPrimitive -> {
                val content = if (value.isString) value.content else value.toString()
                resolveRawValue(content)
            }
            else -> ResolvedComponentValue.Unknown(value.toString())
        }
    }

    private fun resolveRawValue(raw: String?): ResolvedComponentValue {
        if (raw == null) return ResolvedComponentValue.Unknown(null)
        raw.toBooleanStrictOrNull()?.let { return ResolvedComponentValue.BooleanValue(it) }
        raw.toDoubleOrNull()?.let { return ResolvedComponentValue.NumberValue(it) }
        return ResolvedComponentValue.TextValue(raw)
    }
}
