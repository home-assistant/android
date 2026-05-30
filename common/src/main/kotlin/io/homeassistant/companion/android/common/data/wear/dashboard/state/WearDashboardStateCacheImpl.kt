package io.homeassistant.companion.android.common.data.wear.dashboard.state

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WearDashboardStateCacheImpl @Inject constructor() : WearDashboardStateCache {

    private val states = MutableStateFlow<Map<String, WearDashboardResolvedState>>(emptyMap())
    private val mutex = Mutex()

    override fun getState(dashboardId: String): WearDashboardResolvedState? = states.value[dashboardId]

    override fun observeState(dashboardId: String): Flow<WearDashboardResolvedState?> {
        return states.map { it[dashboardId] }
    }

    override suspend fun updateState(dashboardId: String, state: WearDashboardResolvedState) {
        mutex.withLock {
            states.update { current -> current + (dashboardId to state) }
        }
    }

    override suspend fun clearState(dashboardId: String) {
        mutex.withLock {
            states.update { current -> current - dashboardId }
        }
    }

    override suspend fun clearAll() {
        mutex.withLock {
            states.value = emptyMap()
        }
    }
}
