package io.homeassistant.companion.android.common.actions

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WearActionUseCaseImpl @Inject constructor(
    private val repository: WearActionRepository
) : WearActionUseCase {
    override fun getAllActions(): Flow<List<WearAction>> = repository.getAllActions()
    override suspend fun createAction(action: WearAction): Boolean = repository.createAction(action)
    override suspend fun deleteAction(id: Long): Boolean = repository.deleteAction(id)
    override suspend fun deleteAction(action: WearAction): Boolean = repository.deleteAction(action)
}