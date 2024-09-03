package io.homeassistant.companion.android.common.data

import kotlinx.coroutines.flow.Flow

interface BaseDaoRepository<T> {

    suspend fun get(id: Int): T?

    suspend fun add(entity: T)

    suspend fun delete(id: Int)

    suspend fun deleteAll(ids: IntArray)

    suspend fun getAll(): List<T>

    fun getAllFlow(): Flow<List<T>>
}
