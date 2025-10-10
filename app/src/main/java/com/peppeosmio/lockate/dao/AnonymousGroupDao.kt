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

    @Query("UPDATE anonymous_group SET isMember = :isMember WHERE id = :anonymousGroupId")
    suspend fun setAGIsMember(anonymousGroupId: String, isMember: Boolean)

    @Query("UPDATE anonymous_group SET existsRemote = :existsRemote WHERE id = :anonymousGroupId")
    suspend fun setAGExistsRemote(anonymousGroupId: String, existsRemote: Boolean)

    @Query("UPDATE anonymous_group SET sendLocation = :sendLocation WHERE id = :anonymousGroupId")
    suspend fun setAGSendLocation(anonymousGroupId: String, sendLocation: Boolean)

    @Query("""
        UPDATE anonymous_group 
        SET adminTokenCipher = :adminTokenCipher, 
        adminTokenIv = :adminTokenIv
        WHERE id = :anonymousGroupId
        """)
    suspend fun setAGAdminToken(
        anonymousGroupId: String,
        adminTokenCipher: ByteArray,
        adminTokenIv: ByteArray,
    )

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
        SELECT * FROM anonymous_group 
        WHERE sendLocation = 1 AND isMember = 1 AND existsRemote = 1
        ORDER BY createdAt DESC, id DESC
    """
    )
    suspend fun listAGsToSendLocation(): List<AnonymousGroupEntity>

    @Query(
        """
        UPDATE anonymous_group_member
        SET lastLatitude = :lastLatitude, lastLongitude = :lastLongitude,
        lastSeen = :lastSeen
        WHERE id = :agMemberId
    """
    )
    suspend fun setAGMemberLastLocation(
        agMemberId: String,
        lastLatitude: Double,
        lastLongitude: Double,
        lastSeen: Long
    ): Int

    @Query("DELETE FROM anonymous_group WHERE connectionSettingsId = :connectionSettingsId")
    suspend fun deleteAllAGsOfConnection(connectionSettingsId: Long)
}
