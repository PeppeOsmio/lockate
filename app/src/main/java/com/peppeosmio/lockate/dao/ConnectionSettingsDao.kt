package com.peppeosmio.lockate.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.peppeosmio.lockate.data.anonymous_group.database.ConnectionSettingsEntity
import com.peppeosmio.lockate.domain.ConnectionSettings

@Dao
interface ConnectionSettingsDao {
    @Query("SELECT * FROM connection_settings WHERE id = :connectionSettingsId")
    suspend fun getConnectionSettingsById(connectionSettingsId: Long): ConnectionSettingsEntity?

    @Query("SELECT * FROM connection_settings ORDER BY id ASC LIMIT 1")
    suspend fun getFirstConnectionSettings(): ConnectionSettingsEntity?

    @Query("SELECT * FROM connection_settings ORDER BY id")
    suspend fun listConnectionSettings(): List<ConnectionSettingsEntity>

    @Insert
    suspend fun insertConnectionSettings(connectionSettingsEntity: ConnectionSettingsEntity): Long

    @Query("DELETE FROM connection_settings WHERE id = :connectionSettingsId")
    suspend fun deleteConnectionSettings(connectionSettingsId: Long)
}