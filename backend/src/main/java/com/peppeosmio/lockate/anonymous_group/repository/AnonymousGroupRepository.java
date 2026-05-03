package com.peppeosmio.lockate.anonymous_group.repository;

import com.peppeosmio.lockate.anonymous_group.entity.AnonymousGroupEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnonymousGroupRepository
        extends CrudRepository<AnonymousGroupEntity, UUID> {

    @Modifying
    @Query("DELETE FROM AnonymousGroupEntity WHERE id = :anonymousGroupId")
    void deleteAnonymousGroup(@Param("anonymousGroupId") UUID anonymousGroupId);
}
