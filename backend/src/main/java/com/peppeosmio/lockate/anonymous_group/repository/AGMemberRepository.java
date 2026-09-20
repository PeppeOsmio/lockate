package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AGMemberRepository extends CrudRepository<AGMemberEntity, UUID> {
  int countByAnonymousGroupId(UUID anonymousGroupId);

  int countByAnonymousGroupIdIn(Collection<UUID> anonymousGroupIds);

  Optional<AGMemberEntity> findByIdAndAnonymousGroupId(UUID id, UUID anonymousGroupId);

  @Query(
      """
        SELECT m FROM AGMemberEntity m
        LEFT JOIN FETCH m.lastLocation
        WHERE m.anonymousGroupId = :anonymousGroupId
    """)
  List<AGMemberEntity> findMembersWithLastLocation(
      @Param("anonymousGroupId") UUID anonymousGroupId);

  @Query(
      """
        SELECT m.anonymousGroupId AS groupId, COUNT(m) AS memberCount, MAX(l.timestamp) AS lastLocationAt
        FROM AGMemberEntity m LEFT JOIN m.lastLocation l
        WHERE m.anonymousGroupId IN :anonymousGroupIds
        GROUP BY m.anonymousGroupId
    """)
  List<AGGroupSummaryProjection> summarizeByGroupIds(
      @Param("anonymousGroupIds") Collection<UUID> anonymousGroupIds);
}
