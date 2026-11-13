package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How the pushes for one group reach us: the server sends [registrationId] in clear and the payload
 * sealed with the key, which is kept encrypted with the Android Keystore like the group key. The id
 * only has to be unique on this phone.
 */
@Entity(
    tableName = "push_registration",
    foreignKeys =
        [
            ForeignKey(
                entity = AnonymousGroupEntity::class,
                parentColumns = ["internalId"],
                childColumns = ["anonymousGroupInternalId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("registrationId", unique = true)],
)
data class PushRegistrationEntity(
    @PrimaryKey val anonymousGroupInternalId: Long,
    val registrationId: ByteArray,
    val keyCipher: ByteArray,
    val keyIv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PushRegistrationEntity) return false
        return anonymousGroupInternalId == other.anonymousGroupInternalId &&
            registrationId.contentEquals(other.registrationId) &&
            keyCipher.contentEquals(other.keyCipher) &&
            keyIv.contentEquals(other.keyIv)
    }

    override fun hashCode(): Int {
        var result = anonymousGroupInternalId.hashCode()
        result = 31 * result + registrationId.contentHashCode()
        result = 31 * result + keyCipher.contentHashCode()
        result = 31 * result + keyIv.contentHashCode()
        return result
    }
}
