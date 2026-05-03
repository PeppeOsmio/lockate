package com.peppeosmio.lockate.api_key;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApiKeyRepository extends CrudRepository<ApiKeyEntity, UUID> {
}
