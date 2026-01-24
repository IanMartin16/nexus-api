package com.evilink.nexus_api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
  Optional<ConversationEntity> findBySessionIdAndProduct(String sessionId, String product);
}
