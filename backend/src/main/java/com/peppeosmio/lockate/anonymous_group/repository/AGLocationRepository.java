package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AGMemberEntity;
import com.peppeosmio.lockate.anonymous_group.entity.AGMemberLocationEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AGLocationRepository
        extends CrudRepository<AGMemberLocationEntity, UUID> {
    @NativeQuery(value = """
                WITH ranked_locations AS (
                    SELECT
                        l.*,
                        m.anonymous_group_id as m_anonymous_group_id,
                        m.id as m_id,
                        ROW_NUMBER() OVER (
                            PARTITION BY m.id
                            ORDER BY l.timestamp DESC
                        ) AS row_number
                    FROM ag_member_location l
                    JOIN ag_member m ON l.ag_member_id = m.id
                    WHERE m.anonymous_group_id = :anonymousGroupId
                )
                SELECT *
                FROM ranked_locations
                WHERE row_number = 1
            """)
    List<AGMemberLocationEntity> findLastLocationOfMembers(
            @Param("anonymousGroupId") UUID anonymousGroupId);

    @Modifying
    @NativeQuery(value = """
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
