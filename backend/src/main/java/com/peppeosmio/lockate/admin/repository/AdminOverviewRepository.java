package com.peppeosmio.lockate.admin.repository;

import com.peppeosmio.lockate.admin.dto.AdminOverviewDto.Counts;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOverviewRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public AdminOverviewRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Inactive groups are older than {@code inactiveSince} with no position saved since */
  public Counts counts(LocalDateTime inactiveSince) {
    return jdbc.queryForObject(
        """
        SELECT
          (SELECT COUNT(*) FROM anonymous_group) AS groups,
          (SELECT COUNT(*) FROM ag_member) AS members,
          (SELECT COUNT(*) FROM ag_member WHERE push_token IS NOT NULL) AS push_registered,
          (SELECT COUNT(*) FROM ag_circle) AS circles,
          (SELECT COUNT(*) FROM anonymous_group g
           WHERE NOT EXISTS (SELECT 1 FROM ag_member m
                             WHERE m.anonymous_group_id = g.id AND m.is_admin))
            AS groups_without_admin,
          (SELECT COUNT(*) FROM anonymous_group g
           WHERE g.created_at < :inactiveSince
             AND NOT EXISTS (SELECT 1 FROM ag_member m
                             JOIN ag_member_location l ON l.id = m.last_location_id
                             WHERE m.anonymous_group_id = g.id
                               AND l.timestamp >= :inactiveSince))
            AS inactive_groups,
          (SELECT COUNT(*) FROM ag_member_location) AS saved_locations,
          (SELECT COUNT(*) FROM api_key) AS api_keys
        """,
        new MapSqlParameterSource("inactiveSince", inactiveSince),
        (rs, i) ->
            new Counts(
                rs.getLong("groups"),
                rs.getLong("members"),
                rs.getLong("push_registered"),
                rs.getLong("circles"),
                rs.getLong("groups_without_admin"),
                rs.getLong("inactive_groups"),
                rs.getLong("saved_locations"),
                rs.getLong("api_keys")));
  }
}
