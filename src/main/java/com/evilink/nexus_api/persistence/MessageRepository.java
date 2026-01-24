package com.evilink.nexus_api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

  @Query(value = """
    select * from nexus_messages
    where conversation_id = :cid
    order by created_at desc
    limit :limit
  """, nativeQuery = true)
  List<MessageEntity> findLastByConversationId(@Param("cid") UUID conversationId, @Param("limit") int limit);

  long countByConversation_Id(UUID conversationId);

}
