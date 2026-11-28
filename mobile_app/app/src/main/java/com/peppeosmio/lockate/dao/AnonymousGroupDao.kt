package com.peppeosmio.lockate.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.peppeosmio.lockate.data.anonymous_group.database.AGMemberEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AGPlaceEntity
import com.peppeosmio.lockate.data.anonymous_group.database.AnonymousGroupEntity
import com.peppeosmio.lockate.data.anonymous_group.database.MutedTargetEntity
import com.peppeosmio.lockate.data.anonymous_group.database.PushRegistrationEntity
import com.peppeosmio.lockate.data.anonymous_group.mappers.PlaceMapper
import com.peppeosmio.lockate.domain.anonymous_group.Place

data class GroupMemberCount(val anonymousGroupInternalId: Long, val memberCount: Int)

@Dao
interface AnonymousGroupDao {
    @Query(
        "SELECT anonymousGroupInternalId, COUNT(*) AS memberCount FROM ag_member " +
            "GROUP BY anonymousGroupInternalId"
    )
    suspend fun countMembers(): List<GroupMemberCount>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnonymousGroup(agEntity: AnonymousGroupEntity): Long

    @Insert suspend fun insertAGMembers(agMember: List<AGMemberEntity>)

    @Transaction
    suspend fun createAGWithMembers(
        agEntity: AnonymousGroupEntity,
        agMemberEntities: List<AGMemberEntity>,
        places: List<Place> = emptyList(),
    ): Long {
        val anonymousGroupInternalId = insertAnonymousGroup(agEntity)
        val agMembersWithAGId = agMemberEntities.map {
            it.copy(anonymousGroupInternalId = anonymousGroupInternalId)
        }
        insertAGMembers(agMembersWithAGId)
        places.forEach { upsertPlace(anonymousGroupInternalId, it) }
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
        anonymousGroupInternalId: Long,
        agMemberEntities: List<AGMemberEntity>,
    ) {
        deleteAGMembers(anonymousGroupInternalId)
        val agMembersWithAGId = agMemberEntities.map {
            it.copy(anonymousGroupInternalId = anonymousGroupInternalId)
        }
        insertAGMembers(agMembersWithAGId)
    }

    @Query("SELECT * FROM anonymous_group WHERE internalId = :anonymousGroupInternalId")
    suspend fun getAGByInternalId(anonymousGroupInternalId: Long): AnonymousGroupEntity?

    @Query(
        "SELECT * FROM anonymous_group WHERE id = :anonymousGroupId AND connectionId = :connectionId"
    )
    suspend fun getAGByIdAndConnectionId(
        anonymousGroupId: String,
        connectionId: Long,
    ): AnonymousGroupEntity?

    @Query(
        """
        SELECT * FROM anonymous_group
        WHERE connectionId = :connectionSettingsId
        ORDER BY createdAt DESC, id DESC
    """
    )
    suspend fun listAnonymousGroupsOfConnection(
        connectionSettingsId: Long
    ): List<AnonymousGroupEntity>

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

    @Query(
        "UPDATE anonymous_group SET isMember = :isMember WHERE internalId = :anonymousGroupInternalId"
    )
    suspend fun setAGIsMember(anonymousGroupInternalId: Long, isMember: Boolean)

    @Query(
        "UPDATE anonymous_group SET existsRemote = :existsRemote WHERE internalId = :anonymousGroupInternalId"
    )
    suspend fun setAGExistsRemote(anonymousGroupInternalId: Long, existsRemote: Boolean)

    @Query(
        "UPDATE anonymous_group SET sendLocation = :sendLocation WHERE internalId = :anonymousGroupInternalId"
    )
    suspend fun setAGSendLocation(anonymousGroupInternalId: Long, sendLocation: Boolean)

    @Query(
        """
        SELECT * FROM anonymous_group
        WHERE isMember = 1 AND existsRemote = 1 AND sendLocation = 1
        ORDER BY internalId DESC
        """
    )
    suspend fun listAGsToShare(): List<AnonymousGroupEntity>

    @Query(
        """
        SELECT * FROM anonymous_group
        WHERE isMember = 1 AND existsRemote = 1
        ORDER BY internalId DESC
        """
    )
    suspend fun listMemberAGs(): List<AnonymousGroupEntity>

    @Query(
        """
        SELECT * FROM ag_member
        WHERE id = :id AND anonymousGroupInternalId = :agInternalId
        """
    )
    suspend fun getAGMemberByIdAndAGInternalId(id: String, agInternalId: Long): AGMemberEntity?

    @Query(
        "UPDATE ag_member SET isAGAdmin = :isAGAdmin WHERE id = :id AND anonymousGroupInternalId = :agInternalId"
    )
    suspend fun setAGMemberIsAGAdmin(id: String, agInternalId: Long, isAGAdmin: Boolean)

    @Query(
        "UPDATE anonymous_group SET memberIsAGAdmin = :memberIsAGAdmin WHERE internalId = :anonymousGroupInternalId"
    )
    suspend fun setAGMemberIsAGAdminSelf(anonymousGroupInternalId: Long, memberIsAGAdmin: Boolean)

    @Query(
        """
        UPDATE ag_member
        SET lastLatitude = :lastLatitude, lastLongitude = :lastLongitude,
        lastSeen = :lastSeen, lastActivity = :lastActivity
        WHERE internalId = :agMemberInternalId
        """
    )
    suspend fun setAGMemberLastLocation(
        agMemberInternalId: Long,
        lastLatitude: Double,
        lastLongitude: Double,
        lastSeen: Long,
        lastActivity: String,
    ): Int

    @Query("DELETE FROM ag_member WHERE id = :id AND anonymousGroupInternalId = :agInternalId")
    suspend fun deleteAGMemberById(id: String, agInternalId: Long)

    @Insert suspend fun insertAGMember(agMember: AGMemberEntity): Long

    @Query(
        "SELECT * FROM ag_place WHERE anonymousGroupInternalId = :anonymousGroupInternalId ORDER BY internalId"
    )
    suspend fun listPlaces(anonymousGroupInternalId: Long): List<AGPlaceEntity>

    @Insert suspend fun insertPlace(place: AGPlaceEntity)

    @Query(
        """
        UPDATE ag_place
        SET name = :name, latitude = :latitude, longitude = :longitude,
        radiusMeters = :radiusMeters
        WHERE id = :id AND anonymousGroupInternalId = :agInternalId
        """
    )
    suspend fun updatePlace(
        id: String,
        agInternalId: Long,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    )

    @Query("DELETE FROM ag_place WHERE id = :id AND anonymousGroupInternalId = :agInternalId")
    suspend fun deletePlace(id: String, agInternalId: Long)

    /** Makes the places of the group the ones given, dropping the others. */
    @Transaction
    suspend fun syncPlaces(agInternalId: Long, places: List<Place>) {
        val existing = listPlaces(agInternalId).associateBy { it.id }
        existing.keys
            .filter { id -> places.none { it.id == id } }
            .forEach { deletePlace(it, agInternalId) }
        places.forEach { upsertPlace(agInternalId, it) }
    }

    @Query("SELECT * FROM ag_place WHERE id = :id AND anonymousGroupInternalId = :agInternalId")
    suspend fun getPlace(id: String, agInternalId: Long): AGPlaceEntity?

    /**
     * The place can arrive twice at about the same time, from the server's answer to whoever saved
     * it and from the group's channel, so what is already there is read inside the transaction.
     */
    @Transaction
    suspend fun upsertPlace(agInternalId: Long, place: Place) {
        if (getPlace(place.id, agInternalId) == null) {
            insertPlace(PlaceMapper.domainToEntity(place, agInternalId))
            return
        }
        updatePlace(
            id = place.id,
            agInternalId = agInternalId,
            name = place.name,
            latitude = place.center.latitude,
            longitude = place.center.longitude,
            radiusMeters = place.radiusMeters,
        )
    }

    @Query("SELECT targetId FROM muted_target WHERE anonymousGroupInternalId = :agInternalId")
    suspend fun listMutedTargets(agInternalId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMutedTarget(mutedTarget: MutedTargetEntity)

    @Query(
        "DELETE FROM muted_target WHERE anonymousGroupInternalId = :agInternalId AND targetId = :targetId"
    )
    suspend fun deleteMutedTarget(agInternalId: Long, targetId: String)

    @Query("SELECT * FROM push_registration WHERE anonymousGroupInternalId = :agInternalId")
    suspend fun getPushRegistration(agInternalId: Long): PushRegistrationEntity?

    @Query("SELECT * FROM push_registration WHERE registrationId = :registrationId")
    suspend fun getPushRegistrationById(registrationId: ByteArray): PushRegistrationEntity?

    @Insert suspend fun insertPushRegistration(registration: PushRegistrationEntity)
}
