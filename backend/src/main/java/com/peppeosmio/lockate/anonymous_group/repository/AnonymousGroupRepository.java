package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnonymousGroupRepository extends JpaRepository<AnonymousGroupEntity, UUID> {

  @Modifying
  @Query("DELETE FROM AnonymousGroupEntity WHERE id = :anonymousGroupId")
  void deleteAnonymousGroup(@Param("anonymousGroupId") UUID anonymousGroupId);
}
