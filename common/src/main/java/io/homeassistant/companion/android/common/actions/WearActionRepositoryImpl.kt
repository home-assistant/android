package io.homeassistant.companion.android.common.actions

import io.homeassistant.companion.android.common.database.WearActionsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class WearActionRepositoryImpl @Inject constructor(
    private val actionsDao: WearActionsDao
) : WearActionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllActions(): Flow<List<WearAction>> = actionsDao.allActions().flowOn(Dispatchers.IO)

    override suspend fun createAction(action: WearAction): Boolean {
        return actionsDao.createAction(action) != -1L
    }

    override suspend fun deleteAction(id: Long): Boolean {
        return actionsDao.deleteAction(id) > 0
    }

    override suspend fun deleteAction(action: WearAction): Boolean {
        return actionsDao.deleteAction(action) > 0
    }
}