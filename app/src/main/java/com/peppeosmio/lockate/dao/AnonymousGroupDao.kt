package com.peppeosmio.lockate.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity

@Dao
interface AnonymousGroupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnonymousGroup(agEntity: AnonymousGroupEntity)

    @Insert
    suspend fun insertAGMembers(agMember: List<AGMemberEntity>)

    @Transaction
    suspend fun createAGWithMembers(
        agEntity: AnonymousGroupEntity, agMemberEntities: List<AGMemberEntity>
    ) {
        insertAnonymousGroup(agEntity)
        val agMembersWithAGId = agMemberEntities.map { it.copy(anonymousGroupId = agEntity.id) }
        insertAGMembers(agMembersWithAGId)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM anonymous_group_member WHERE id = :agMemberId)")
    suspend fun existsAGMember(agMemberId: String): Boolean

    @Query("DELETE FROM anonymous_group_member WHERE anonymousGroupId = :anonymousGroupId")
    suspend fun deleteAGMembers(anonymousGroupId: String)

    @Query("DELETE FROM anonymous_group_member WHERE id = :agMemberId")
    suspend fun deleteAGMember(agMemberId: String)

    @Transaction
    suspend fun setAGMembers(
        anonymousGroupId: String, agMemberEntities: List<AGMemberEntity>
    ) {
        deleteAGMembers(anonymousGroupId)
        val agMembersWithAGId =
            agMemberEntities.map { it.copy(anonymousGroupId = anonymousGroupId) }
        insertAGMembers(agMembersWithAGId)
    }

    @Query("SELECT * FROM anonymous_group WHERE id = :anonymousGroupId")
    suspend fun getAnonymousGroupById(anonymousGroupId: String): AnonymousGroupEntity?

    @Query(
        """
        SELECT * FROM anonymous_group
        WHERE connectionSettingsId = :connectionSettingsId
        ORDER BY createdAt DESC, id DESC
    """
    )
    suspend fun listAnonymousGroupsOfConnection(connectionSettingsId: Long): List<AnonymousGroupEntity>

    @Query(
        """
        SELECT * FROM anonymous_group_member
        WHERE anonymousGroupId = :anonymousGroupId
        ORDER BY createdAt DESC, id DESC
    """
    )
    suspend fun listAGMembers(anonymousGroupId: String): List<AGMemberEntity>

    @Query("DELETE FROM anonymous_group WHERE id = :anonymousGroupId")
    suspend fun deleteAnonymousGroupById(anonymousGroupId: String)

    @Query("UPDATE anonymous_group SET isMember = 0 WHERE id = :anonymousGroupId")
    suspend fun setAGIsMemberFalse(anonymousGroupId: String)

    @Query("UPDATE anonymous_group SET existsRemote = 0 WHERE id = :anonymousGroupId")
    suspend fun setAGExistsRemoteFalse(anonymousGroupId: String)

    @Query("UPDATE anonymous_group SET sendLocation = :sendLocation WHERE id = :anonymousGroupId")
    suspend fun setAGSendLocation(anonymousGroupId: String, sendLocation: Boolean)

    @Query("UPDATE anonymous_group SET adminToken = :adminToken WHERE id = :anonymousGroupId")
    suspend fun setAGAdminToken(anonymousGroupId: String, adminToken: ByteArray)

    @Query(
        """
        SELECT * FROM anonymous_group 
        WHERE sendLocation = 1 AND isMember = 1 AND existsRemote = 1 AND connectionSettingsId = :connectionSettingsId
        ORDER BY createdAt DESC, id DESC
    """
    )
    suspend fun listAGToSendLocationOfConnection(connectionSettingsId: Long): List<AnonymousGroupEntity>

    @Query(
        """
        UPDATE anonymous_group_member
        SET lastLatitude = :lastLocationLatitude, lastLongitude = :lastLocationLongitude,
        lastSeen = :lastLocationTimestamp
        WHERE id = :agMemberId
    """
    )
    suspend fun setAGMemberLastLocation(
        agMemberId: String,
        lastLocationLatitude: Double,
        lastLocationLongitude: Double,
        lastLocationTimestamp: Long
    ): Int

    @Query("DELETE FROM anonymous_group WHERE connectionSettingsId = :connectionSettingsId")
    suspend fun deleteAllAGsOfConnection(connectionSettingsId: Long)
}
