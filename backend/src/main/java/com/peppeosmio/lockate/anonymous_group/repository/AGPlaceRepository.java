package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AGPlaceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AGPlaceRepository extends JpaRepository<AGPlaceEntity, UUID> {
  List<AGPlaceEntity> findByAnonymousGroupIdOrderByCreatedAtAscIdAsc(UUID anonymousGroupId);

  Optional<AGPlaceEntity> findByIdAndAnonymousGroupId(UUID id, UUID anonymousGroupId);

  int countByAnonymousGroupId(UUID anonymousGroupId);
}
