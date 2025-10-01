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
    val name: String,
    val createdAt: Long,
    val joinedAt: Long,

    val isMember: Boolean,
    val existsRemote: Boolean,
    val sendLocation: Boolean,
    val connectionSettingsId: Long,

    val memberName: String,
    val memberId: String,
    val memberTokenCipher: ByteArray,
    val memberTokenIv: ByteArray,

    val adminTokenCipher: ByteArray?,
    val adminTokenIv: ByteArray?,

    val keyCipher: ByteArray,
    val keyIv: ByteArray,
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
        if (connectionSettingsId != other.connectionSettingsId) return false
        if (id != other.id) return false
        if (name != other.name) return false
        if (memberName != other.memberName) return false
        if (memberId != other.memberId) return false
        if (!memberTokenCipher.contentEquals(other.memberTokenCipher)) return false
        if (!memberTokenIv.contentEquals(other.memberTokenIv)) return false
        if (adminTokenCipher != null) {
            if (other.adminTokenCipher == null) return false
            if (!adminTokenCipher.contentEquals(other.adminTokenCipher)) return false
        } else if (other.adminTokenCipher != null) return false
        if (adminTokenIv != null) {
            if (other.adminTokenIv == null) return false
            if (!adminTokenIv.contentEquals(other.adminTokenIv)) return false
        } else if (other.adminTokenIv != null) return false
        if (!keyCipher.contentEquals(other.keyCipher)) return false
        if (!keyIv.contentEquals(other.keyIv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = createdAt.hashCode()
        result = 31 * result + joinedAt.hashCode()
        result = 31 * result + isMember.hashCode()
        result = 31 * result + existsRemote.hashCode()
        result = 31 * result + sendLocation.hashCode()
        result = 31 * result + connectionSettingsId.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + memberName.hashCode()
        result = 31 * result + memberId.hashCode()
        result = 31 * result + memberTokenCipher.contentHashCode()
        result = 31 * result + memberTokenIv.contentHashCode()
        result = 31 * result + (adminTokenCipher?.contentHashCode() ?: 0)
        result = 31 * result + (adminTokenIv?.contentHashCode() ?: 0)
        result = 31 * result + keyCipher.contentHashCode()
        result = 31 * result + keyIv.contentHashCode()
        return result
    }
}