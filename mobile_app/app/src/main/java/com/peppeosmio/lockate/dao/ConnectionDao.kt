package com.peppeosmio.lockate.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionEntity

data class ServerGroupCount(val connectionId: Long, val groupCount: Int)

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connection WHERE id = :connectionId")
    suspend fun getConnectionSettingsById(connectionId: Long): ConnectionEntity?

    @Query("SELECT * FROM connection ORDER BY id ASC LIMIT 1")
    suspend fun getFirstConnection(): ConnectionEntity?

    @Query("SELECT * FROM connection ORDER BY name COLLATE NOCASE")
    suspend fun listConnections(): List<ConnectionEntity>

    @Query("SELECT * FROM connection WHERE url = :url")
    suspend fun getConnectionByUrl(url: String): ConnectionEntity?

    @Query("SELECT connectionId, COUNT(*) AS groupCount FROM anonymous_group GROUP BY connectionId")
    suspend fun countGroups(): List<ServerGroupCount>

    @Insert suspend fun insertConnection(connectionEntity: ConnectionEntity): Long

    @Update suspend fun updateConnection(connectionEntity: ConnectionEntity)

    @Query("DELETE FROM connection WHERE id = :connectionId")
    suspend fun deleteConnectionSettings(connectionId: Long)
}
