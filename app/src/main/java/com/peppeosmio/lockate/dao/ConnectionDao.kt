package com.peppeosmio.lockate.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connection WHERE id = :connectionId")
    suspend fun getConnectionSettingsById(connectionId: Long): ConnectionEntity?

    @Query("SELECT * FROM connection ORDER BY id ASC LIMIT 1")
    suspend fun getFirstConnectionSettings(): ConnectionEntity?

    @Query("SELECT * FROM connection ORDER BY id")
    suspend fun listConnectionSettings(): List<ConnectionEntity>

    @Insert
    suspend fun insertConnectionSettings(connectionEntity: ConnectionEntity): Long

    @Query("DELETE FROM connection WHERE id = :connectionId")
    suspend fun deleteConnectionSettings(connectionId: Long)
}