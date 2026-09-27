package com.peppeosmio.lockate.api_key;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {}
