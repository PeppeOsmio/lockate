package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ag_circle",
    foreignKeys =
        [
            ForeignKey(
                entity = AnonymousGroupEntity::class,
                parentColumns = ["internalId"],
                childColumns = ["anonymousGroupInternalId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index(
                "anonymousGroupInternalId",
                "id",
                unique = true,
                name = "idx_ag_circle_group_id",
            )
        ],
)
data class AGCircleEntity(
    @PrimaryKey(autoGenerate = true) val internalId: Long,
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val anonymousGroupInternalId: Long,
)
