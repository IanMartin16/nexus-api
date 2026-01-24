package com.evilink.nexus_api.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nexus_messages",
       indexes = @Index(name = "idx_nexus_msg_conv_created", columnList = "conversation_id,created_at"))
public class MessageEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private ConversationEntity conversation;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected MessageEntity() {}

  public MessageEntity(UUID id, ConversationEntity conversation, String role, String content, Instant createdAt) {
    this.id = id;
    this.conversation = conversation;
    this.role = role;
    this.content = content;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }
  public ConversationEntity getConversation() { return conversation; }
  public String getRole() { return role; }
  public String getContent() { return content; }
  public Instant getCreatedAt() { return createdAt; }
}
