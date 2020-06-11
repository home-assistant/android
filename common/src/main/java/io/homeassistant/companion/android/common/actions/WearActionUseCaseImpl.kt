package io.homeassistant.companion.android.common.actions

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WearActionUseCaseImpl @Inject constructor(
    private val repository: WearActionRepository
) : WearActionUseCase {
    override fun getAllActions(): Flow<List<WearAction>> = repository.getAllActions()
    override suspend fun saveAction(action: WearAction): Boolean = repository.saveAction(action)
    override suspend fun deleteAction(id: Long): Boolean = repository.deleteAction(id)
    override suspend fun deleteAction(action: WearAction): Boolean = repository.deleteAction(action)
}
