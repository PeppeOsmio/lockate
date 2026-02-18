package com.peppeosmio.lockate.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connection WHERE id = :connectionId")
    suspend fun getConnectionSettingsById(connectionId: Long): ConnectionEntity?

    @Query("SELECT * FROM connection ORDER BY id ASC LIMIT 1")
    suspend fun getFirstConnection(): ConnectionEntity?

    @Query("SELECT * FROM connection ORDER BY id")
    suspend fun listConnections(): List<ConnectionEntity>

    @Insert
    suspend fun insertConnection(connectionEntity: ConnectionEntity): Long

    @Update
    suspend fun updateConnection(connectionEntity: ConnectionEntity)

    @Query("DELETE FROM connection WHERE id = :connectionId")
    suspend fun deleteConnectionSettings(connectionId: Long)
}