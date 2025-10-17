package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ag_member", foreignKeys = [ForeignKey(
        entity = AnonymousGroupEntity::class,
        parentColumns = ["internalId"],
        childColumns = ["anonymousGroupInternalId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index(
        "anonymousGroupInternalId",
        "id",
        name = "idx_ag_member_anonymousGroupInternalId_id"
    )]
)
data class AGMemberEntity(
    @PrimaryKey(autoGenerate = true) val internalId: Long,
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastSeen: Long?,
    val anonymousGroupInternalId: Long,
) {}