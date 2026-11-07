package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AGLocationRepository extends CrudRepository<AGMemberLocationEntity, UUID> {
  @Modifying
  @NativeQuery(
      value =
          """
            DELETE FROM ag_member_location l
            WHERE l.timestamp < :cutoff
            AND l.id NOT IN (
                SELECT id FROM (
                    SELECT DISTINCT ON (ag_member_id) id, timestamp
                    FROM ag_member_location
                    ORDER BY ag_member_id, timestamp DESC
                ) latest_per_member
            )
            """)
  int deleteOldLocations(@Param("cutoff") Instant cutoff);

  Optional<AGMemberLocationEntity> findFirstByAgMemberEntityOrderByTimestampDescIdDesc(
      AGMemberEntity agMemberEntity);
}
