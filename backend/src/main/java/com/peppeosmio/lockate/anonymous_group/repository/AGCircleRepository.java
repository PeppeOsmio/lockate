package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AGCircleEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AGCircleRepository extends JpaRepository<AGCircleEntity, UUID> {
  List<AGCircleEntity> findByAnonymousGroupIdOrderByCreatedAtAscIdAsc(UUID anonymousGroupId);

  Optional<AGCircleEntity> findByIdAndAnonymousGroupId(UUID id, UUID anonymousGroupId);

  int countByAnonymousGroupId(UUID anonymousGroupId);
}
