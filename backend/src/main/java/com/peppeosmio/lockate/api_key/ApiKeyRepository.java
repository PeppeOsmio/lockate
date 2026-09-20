package com.peppeosmio.lockate.api_key;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiKeyRepository extends CrudRepository<ApiKeyEntity, UUID> {}
