package io.homeassistant.companion.android.common.actions

import kotlinx.coroutines.flow.Flow

interface WearActionUseCase {
    fun getAllActions(): Flow<List<WearAction>>
    suspend fun createAction(action: WearAction): Boolean
    suspend fun deleteAction(id: Long): Boolean
    suspend fun deleteAction(action: WearAction): Boolean
}