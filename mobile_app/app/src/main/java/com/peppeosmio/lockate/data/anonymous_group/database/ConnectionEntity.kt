package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection")
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(defaultValue = "") val name: String,
    val url: String,
    val apiKey: String?,
    val username: String?,
    val authToken: String?
)
