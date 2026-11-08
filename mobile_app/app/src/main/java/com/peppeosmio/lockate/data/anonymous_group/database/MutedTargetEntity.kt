package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.Entity
import androidx.room.ForeignKey

/** A circle or a member whose arrivals and departures don't notify, in one group. */
@Entity(
    tableName = "muted_target",
    primaryKeys = ["anonymousGroupInternalId", "targetId"],
    foreignKeys =
        [
            ForeignKey(
                entity = AnonymousGroupEntity::class,
                parentColumns = ["internalId"],
                childColumns = ["anonymousGroupInternalId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class MutedTargetEntity(
    val anonymousGroupInternalId: Long,
    val targetId: String,
)
