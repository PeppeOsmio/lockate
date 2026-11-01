package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnonymousGroupRepository extends JpaRepository<AnonymousGroupEntity, UUID> {

  @Modifying
  @Query("DELETE FROM AnonymousGroupEntity WHERE id = :anonymousGroupId")
  void deleteAnonymousGroup(@Param("anonymousGroupId") UUID anonymousGroupId);

  /** Holds the group's row until the transaction ends, so its writes go one at a time. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT g FROM AnonymousGroupEntity g WHERE g.id = :anonymousGroupId")
  Optional<AnonymousGroupEntity> findByIdForUpdate(@Param("anonymousGroupId") UUID anonymousGroupId);
}
