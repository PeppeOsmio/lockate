package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AGMemberRepository extends JpaRepository<AGMemberEntity, UUID> {
  int countByAnonymousGroupId(UUID anonymousGroupId);

  int countByAnonymousGroupIdIn(Collection<UUID> anonymousGroupIds);

  Optional<AGMemberEntity> findByIdAndAnonymousGroupId(UUID id, UUID anonymousGroupId);

  List<AGMemberEntity> findByAnonymousGroupIdAndPushTokenIsNotNull(UUID anonymousGroupId);

  /** Only if the member hasn't registered again meanwhile. */
  @Transactional
  @Modifying
  @Query(
      """
        UPDATE AGMemberEntity m
        SET m.pushToken = NULL, m.pushRegistrationId = NULL, m.pushKey = NULL
        WHERE m.id = :id AND m.pushRegistrationId = :registrationId
    """)
  int clearPushRegistration(@Param("id") UUID id, @Param("registrationId") byte[] registrationId);

  @Query(
      """
        SELECT m FROM AGMemberEntity m
        LEFT JOIN FETCH m.lastLocation
        WHERE m.anonymousGroupId = :anonymousGroupId
        ORDER BY m.createdAt, m.id
    """)
  List<AGMemberEntity> findMembersWithLastLocation(
      @Param("anonymousGroupId") UUID anonymousGroupId);
}
