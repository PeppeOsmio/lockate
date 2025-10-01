package com.peppeosmio.lockate.domain.anonymous_group

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class AnonymousGroup(
    val id: String,
    val name: String,
    val createdAt: LocalDateTime,
    val joinedAt: LocalDateTime,
    val memberName: String,
    val memberId: String,
    val memberToken: ByteArray,
    val adminToken: ByteArray?,
    val isMember: Boolean,
    val existsRemote: Boolean,
    val sendLocation: Boolean,
    val key: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnonymousGroup

        if (isMember != other.isMember) return false
        if (existsRemote != other.existsRemote) return false
        if (sendLocation != other.sendLocation) return false
        if (id != other.id) return false
        if (name != other.name) return false
        if (createdAt != other.createdAt) return false
        if (joinedAt != other.joinedAt) return false
        if (memberName != other.memberName) return false
        if (memberId != other.memberId) return false
        if (!memberToken.contentEquals(other.memberToken)) return false
        if (adminToken != null) {
            if (other.adminToken == null) return false
            if (!adminToken.contentEquals(other.adminToken)) return false
        } else if (other.adminToken != null) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isMember.hashCode()
        result = 31 * result + existsRemote.hashCode()
        result = 31 * result + sendLocation.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + joinedAt.hashCode()
        result = 31 * result + memberName.hashCode()
        result = 31 * result + memberId.hashCode()
        result = 31 * result + memberToken.contentHashCode()
        result = 31 * result + (adminToken?.contentHashCode() ?: 0)
        result = 31 * result + key.contentHashCode()
        return result
    }
}
