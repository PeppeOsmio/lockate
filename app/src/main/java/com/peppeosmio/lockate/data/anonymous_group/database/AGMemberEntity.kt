package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "anonymous_group_member",
    foreignKeys = [
        ForeignKey(
            entity = AnonymousGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["anonymousGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ], indices = [Index("anonymousGroupId")])
data class AGMemberEntity(
    @PrimaryKey val id : String,
    val name: String,
    val createdAt: Long,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastSeen: Long?,
    val anonymousGroupId: String = "",
) {
}