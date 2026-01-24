package com.evilink.nexus_api.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nexus_conversations",
       indexes = @Index(name = "idx_nexus_conv_session_product", columnList = "session_id,product"))
public class ConversationEntity {

  @Id
  private UUID id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "product", nullable = false)
  private String product;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  protected ConversationEntity() {}

  public ConversationEntity(UUID id, String sessionId, String product, Instant createdAt, Instant lastSeenAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.product = product;
    this.createdAt = createdAt;
    this.lastSeenAt = lastSeenAt;
  }

  public UUID getId() { return id; }
  public String getSessionId() { return sessionId; }
  public String getProduct() { return product; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getLastSeenAt() { return lastSeenAt; }

  public void touch() { this.lastSeenAt = Instant.now(); }
}
