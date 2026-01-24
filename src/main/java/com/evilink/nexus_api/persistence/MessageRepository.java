package com.evilink.nexus_api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

  @Query("""
    select m from MessageEntity m
    where m.conversation.id = :cid
    order by m.createdAt asc
  """)
  List<MessageEntity> findAllByConversationId(@Param("cid") UUID conversationId);
}
