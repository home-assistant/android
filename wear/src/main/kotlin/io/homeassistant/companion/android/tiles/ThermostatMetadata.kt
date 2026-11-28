@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val METADATA_CACHE_MS = 120_000L
private const val METADATA_TIMEOUT_MS = 2_000L

/** Metadata changes less frequently than climate state; keep it out of the command cache. */
@Singleton
class ThermostatMetadata @Inject constructor(
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    private val clock: Clock,
) {
    internal data class Resolved(val name: String, val sourceUnit: String?)

    private data class Cached(val value: String?, val receivedAt: Long)

    private val names = ConcurrentHashMap<ThermostatCommandCoordinator.Target, Cached>()
    private val serverUnits = ConcurrentHashMap<Int, Cached>()

    internal suspend fun resolve(
        target: ThermostatCommandCoordinator.Target,
        entity: EntityDisplayWithoutContext,
    ): Resolved = coroutineScope {
        val name = async {
            cached(names, target) { entitiesForDisplayManager.registryDisplayName(target.serverId, target.entityId) }
        }
        val unit = entity.climateControls?.temperatureUnit?.takeIf { it.isNotBlank() }
            ?: cached(serverUnits, target.serverId) {
                serverManager.webSocketRepository(target.serverId).getConfig()?.unitSystem?.get("temperature")
            }
        Resolved(name.await() ?: entity.name, unit)
    }

    private suspend fun <K : Any> cached(
        cache: ConcurrentHashMap<K, Cached>,
        key: K,
        fetch: suspend () -> String?,
    ): String? {
        val now = clock.now().toEpochMilliseconds()
        cache[key]?.takeIf { now - it.receivedAt in 0L until METADATA_CACHE_MS }?.let { return it.value }
        val value = try {
            withTimeoutOrNull(METADATA_TIMEOUT_MS) { fetch() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.d(error, "Unable to resolve thermostat metadata")
            null
        }
        cache[key] = Cached(value, clock.now().toEpochMilliseconds())
        return value
    }
}
