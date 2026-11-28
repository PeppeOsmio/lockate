package com.peppeosmio.lockate.admin.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** The admin's view of groups and members: counts and times, never an encrypted field. */
@Repository
public class AdminGroupRepository {

  public record GroupRow(
      UUID id,
      LocalDateTime createdAt,
      long memberCount,
      long adminCount,
      long pushRegisteredCount,
      long placeCount,
      LocalDateTime lastLocationAt) {}

  public record MemberRow(
      UUID id,
      UUID groupId,
      LocalDateTime createdAt,
      boolean isAdmin,
      boolean pushRegistered,
      LocalDateTime lastLocationAt) {}

  /**
   * An id prefix (lowercase) and the time since which a group must be inactive, both optional, and
   * whether to keep only the groups without an admin
   */
  public record Filter(String idPrefix, LocalDateTime inactiveSince, boolean noAdmin) {}

  public enum Sort {
    CREATED("g.created_at DESC, g.id"),
    MEMBERS("member_count DESC, g.created_at DESC, g.id"),
    ACTIVITY("last_location_at DESC NULLS LAST, g.created_at DESC, g.id");

    private final String orderBy;

    Sort(String orderBy) {
      this.orderBy = orderBy;
    }
  }

  private static final String GROUPS =
      """
      SELECT g.id, g.created_at,
             COUNT(m.id) AS member_count,
             COUNT(m.id) FILTER (WHERE m.is_admin) AS admin_count,
             COUNT(m.id) FILTER (WHERE m.push_token IS NOT NULL) AS push_registered_count,
             (SELECT COUNT(*) FROM ag_place c WHERE c.anonymous_group_id = g.id) AS place_count,
             MAX(l.timestamp) AS last_location_at
      FROM anonymous_group g
      LEFT JOIN ag_member m ON m.anonymous_group_id = g.id
      LEFT JOIN ag_member_location l ON l.id = m.last_location_id
      """;

  private static final String FILTERED_GROUPS =
      GROUPS
          + """
          WHERE (CAST(:idPrefix AS text) IS NULL
                 OR CAST(g.id AS text) LIKE :idPrefix || '%'
                 OR EXISTS (SELECT 1 FROM ag_member s
                            WHERE s.anonymous_group_id = g.id
                              AND CAST(s.id AS text) LIKE :idPrefix || '%'))
            AND (CAST(:inactiveSince AS timestamp) IS NULL OR g.created_at < :inactiveSince)
          GROUP BY g.id
          HAVING (CAST(:inactiveSince AS timestamp) IS NULL
                  OR MAX(l.timestamp) IS NULL
                  OR MAX(l.timestamp) < :inactiveSince)
             AND (NOT :noAdmin OR COUNT(m.id) FILTER (WHERE m.is_admin) = 0)
          """;

  private final NamedParameterJdbcTemplate jdbc;

  public AdminGroupRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static MapSqlParameterSource params(Filter filter) {
    return new MapSqlParameterSource()
        .addValue("idPrefix", filter.idPrefix(), Types.VARCHAR)
        .addValue("inactiveSince", filter.inactiveSince(), Types.TIMESTAMP)
        .addValue("noAdmin", filter.noAdmin());
  }

  public long countGroups(Filter filter) {
    var count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM (" + FILTERED_GROUPS + ") t", params(filter), Long.class);
    return count == null ? 0 : count;
  }

  public List<GroupRow> findGroups(Filter filter, Sort sort, long offset, int limit) {
    return jdbc.query(
        FILTERED_GROUPS + " ORDER BY " + sort.orderBy + " LIMIT :limit OFFSET :offset",
        params(filter).addValue("limit", limit).addValue("offset", offset),
        (rs, i) -> groupRow(rs));
  }

  public Optional<GroupRow> findGroup(UUID id) {
    return jdbc
        .query(
            GROUPS + " WHERE g.id = :id GROUP BY g.id",
            new MapSqlParameterSource("id", id),
            (rs, i) -> groupRow(rs))
        .stream()
        .findFirst();
  }

  /** The members of the groups, in the order they joined */
  public List<MemberRow> findMembers(Collection<UUID> groupIds) {
    return jdbc.query(
        """
        SELECT m.id, m.anonymous_group_id, m.created_at, m.is_admin,
               m.push_token IS NOT NULL AS push_registered,
               l.timestamp AS last_location_at
        FROM ag_member m
        LEFT JOIN ag_member_location l ON l.id = m.last_location_id
        WHERE m.anonymous_group_id IN (:groupIds)
        ORDER BY m.created_at, m.id
        """,
        new MapSqlParameterSource("groupIds", groupIds),
        (rs, i) ->
            new MemberRow(
                rs.getObject("id", UUID.class),
                rs.getObject("anonymous_group_id", UUID.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getBoolean("is_admin"),
                rs.getBoolean("push_registered"),
                rs.getObject("last_location_at", LocalDateTime.class)));
  }

  private static GroupRow groupRow(ResultSet rs) throws SQLException {
    return new GroupRow(
        rs.getObject("id", UUID.class),
        rs.getObject("created_at", LocalDateTime.class),
        rs.getLong("member_count"),
        rs.getLong("admin_count"),
        rs.getLong("push_registered_count"),
        rs.getLong("place_count"),
        rs.getObject("last_location_at", LocalDateTime.class));
  }
}
