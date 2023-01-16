package io.homeassistant.companion.android.database.notification

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(notification: NotificationItem): Long

    @Query("SELECT * FROM notification_history WHERE id = :id")
    fun get(id: Int): NotificationItem?

    @Query("SELECT * FROM notification_history ORDER BY received DESC")
    fun getAll(): Array<NotificationItem>?

    @Query("SELECT * FROM notification_history ORDER BY received DESC LIMIT (:amount)")
    fun getLastItems(amount: Int): Array<NotificationItem>?

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()
}
