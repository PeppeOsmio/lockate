package com.peppeosmio.lockate.data.anonymous_group.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "anonymous_group", foreignKeys = [ForeignKey(
        entity = ConnectionSettingsEntity::class,
        parentColumns = ["id"],
        childColumns = ["connectionSettingsId"],
        onDelete = ForeignKey.CASCADE
    )], indices = [Index("connectionSettingsId")]
)
data class AnonymousGroupEntity(
    @PrimaryKey val id: String,
    // name
    val name: String,
    val createdAt: Long,
    val joinedAt: Long,
    // member
    val memberName: String,
    val memberId: String,
    val memberToken: ByteArray,
    val adminToken: ByteArray?,
    val isMember: Boolean,
    val existsRemote: Boolean,
    val sendLocation: Boolean,
    // TODO don't save it here
    val memberPassword: String,
    val connectionSettingsId: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnonymousGroupEntity

        if (createdAt != other.createdAt) return false
        if (joinedAt != other.joinedAt) return false
        if (isMember != other.isMember) return false
        if (existsRemote != other.existsRemote) return false
        if (sendLocation != other.sendLocation) return false
        if (id != other.id) return false
        if (name != other.name) return false
        if (memberName != other.memberName) return false
        if (memberId != other.memberId) return false
        if (!memberToken.contentEquals(other.memberToken)) return false
        if (adminToken != null) {
            if (other.adminToken == null) return false
            if (!adminToken.contentEquals(other.adminToken)) return false
        } else if (other.adminToken != null) return false
        if (memberPassword != other.memberPassword) return false
        if (connectionSettingsId != other.connectionSettingsId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = createdAt.hashCode()
        result = 31 * result + joinedAt.hashCode()
        result = 31 * result + isMember.hashCode()
        result = 31 * result + existsRemote.hashCode()
        result = 31 * result + sendLocation.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + memberName.hashCode()
        result = 31 * result + memberId.hashCode()
        result = 31 * result + memberToken.contentHashCode()
        result = 31 * result + (adminToken?.contentHashCode() ?: 0)
        result = 31 * result + memberPassword.hashCode()
        result = 31 * result + connectionSettingsId.hashCode()
        return result
    }
}