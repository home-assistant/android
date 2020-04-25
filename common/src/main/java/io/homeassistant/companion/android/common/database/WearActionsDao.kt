package io.homeassistant.companion.android.common.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.homeassistant.companion.android.common.actions.WearAction
import kotlinx.coroutines.flow.Flow

@Dao
interface WearActionsDao  {

    @Query("SELECT * FROM wear_actions")
    fun allActions(): Flow<List<WearAction>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun createAction(action: WearAction): Long

    @Update
    suspend fun updateAction(action: WearAction): Int

    @Delete
    suspend fun deleteAction(action: WearAction): Int

    @Query("DELETE FROM wear_actions WHERE wear_action_id = :actionId")
    suspend fun deleteAction(actionId: Long): Int

}