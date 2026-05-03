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
    suspend fun insertAnonymousGroup(agEntity: AnonymousGroupEntity): Long

    @Insert
    suspend fun insertAGMembers(agMember: List<AGMemberEntity>)

    @Transaction
    suspend fun createAGWithMembers(
        agEntity: AnonymousGroupEntity, agMemberEntities: List<AGMemberEntity>
    ) : Long {
        val anonymousGroupInternalId = insertAnonymousGroup(agEntity)
        val agMembersWithAGId =
            agMemberEntities.map { it.copy(anonymousGroupInternalId = anonymousGroupInternalId) }
        insertAGMembers(agMembersWithAGId)
        return anonymousGroupInternalId
    }

    @Query("SELECT EXISTS(SELECT 1 FROM ag_member WHERE internalId = :agMemberInternalId)")
    suspend fun existsAGMember(agMemberInternalId: Long): Boolean

    @Query("DELETE FROM ag_member WHERE anonymousGroupInternalId = :anonymousGroupInternalId")
    suspend fun deleteAGMembers(anonymousGroupInternalId: Long)

    @Query("DELETE FROM ag_member WHERE internalId = :agMemberInternalId")
    suspend fun deleteAGMember(agMemberInternalId: Long)

    @Transaction
    suspend fun setAGMembers(
        anonymousGroupInternalId: Long, agMemberEntities: List<AGMemberEntity>
    ) {
        deleteAGMembers(anonymousGroupInternalId)
        val agMembersWithAGId =
            agMemberEntities.map { it.copy(anonymousGroupInternalId = anonymousGroupInternalId) }
        insertAGMembers(agMembersWithAGId)
    }

    @Query("SELECT * FROM anonymous_group WHERE internalId = :anonymousGroupInternalId")
    suspend fun getAGByInternalId(anonymousGroupInternalId: Long): AnonymousGroupEntity?

    @Query("SELECT * FROM anonymous_group WHERE id = :anonymousGroupId AND connectionId = :connectionId")
    suspend fun getAGByIdAndConnectionId(anonymousGroupId: String, connectionId: Long)  : AnonymousGroupEntity?

    @Query(
        """
        SELECT * FROM anonymous_group
        WHERE connectionId = :connectionSettingsId
        ORDER BY createdAt DESC, id DESC
    """
    )
    suspend fun listAnonymousGroupsOfConnection(connectionSettingsId: Long): List<AnonymousGroupEntity>

    @Query(
        """
        SELECT * FROM ag_member
        WHERE anonymousGroupInternalId = :anonymousGroupInternalId
        ORDER BY internalId
    """
    )
    suspend fun listAGMembers(anonymousGroupInternalId: Long): List<AGMemberEntity>

    @Query("DELETE FROM anonymous_group WHERE internalId = :anonymousGroupInternalId")
    suspend fun deleteAGByInternalId(anonymousGroupInternalId: Long)

    @Query("UPDATE anonymous_group SET isMember = :isMember WHERE internalId = :anonymousGroupInternalId")
    suspend fun setAGIsMember(anonymousGroupInternalId: Long, isMember: Boolean)

    @Query("UPDATE anonymous_group SET existsRemote = :existsRemote WHERE internalId = :anonymousGroupInternalId")
    suspend fun setAGExistsRemote(anonymousGroupInternalId: Long, existsRemote: Boolean)

    @Query("UPDATE anonymous_group SET sendLocation = :sendLocation WHERE internalId = :anonymousGroupInternalId")
    suspend fun setAGSendLocation(anonymousGroupInternalId: Long, sendLocation: Boolean)


    @Query(
        """
        SELECT * FROM anonymous_group 
        WHERE sendLocation = 1 AND isMember = 1 AND existsRemote = 1 AND connectionId = :connectionSettingsId
        ORDER BY internalId DESC
    """
    )
    suspend fun listAGToSendLocationOfConnection(connectionSettingsId: Long): List<AnonymousGroupEntity>

    @Query(
        """
        SELECT * FROM anonymous_group 
        WHERE sendLocation = 1 AND isMember = 1 AND existsRemote = 1
        ORDER BY internalId DESC
        """
    )
    suspend fun listAGsToSendLocation(): List<AnonymousGroupEntity>

    @Query(
        """
        SELECT * FROM ag_member
        WHERE id = :id AND anonymousGroupInternalId = :agInternalId
        """
    )
    suspend fun getAGMemberByIdAndAGInternalId(id: String, agInternalId: Long): AGMemberEntity?

    @Query(
        """
        UPDATE ag_member
        SET lastLatitude = :lastLatitude, lastLongitude = :lastLongitude,
        lastSeen = :lastSeen
        WHERE internalId = :agMemberInternalId
        """
    )
    suspend fun setAGMemberLastLocation(
        agMemberInternalId: Long, lastLatitude: Double, lastLongitude: Double, lastSeen: Long
    ): Int

    @Query("DELETE FROM anonymous_group WHERE connectionId = :connectionSettingsId")
    suspend fun deleteAllAGsOfConnection(connectionSettingsId: Long)
}
