package com.evilink.nexus_api.chat;

import com.evilink.nexus_api.openai.OpenAiResponsesClient;
import com.evilink.nexus_api.persistence.ConversationEntity;
import com.evilink.nexus_api.persistence.ConversationRepository;
import com.evilink.nexus_api.persistence.MessageEntity;
import com.evilink.nexus_api.persistence.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ChatMemoryService {

  private final ConversationRepository convRepo;
  private final MessageRepository msgRepo;
  private final OpenAiResponsesClient openAi;

  public ChatMemoryService(ConversationRepository convRepo, MessageRepository msgRepo, OpenAiResponsesClient openAi) {
    this.convRepo = convRepo;
    this.msgRepo = msgRepo;
    this.openAi = openAi;
  }

  @Transactional
  public ConversationEntity getOrCreateConversation(String sessionId, String product) {
    String sid = (sessionId == null) ? "" : sessionId.trim();
    String p = (product == null) ? "" : product.trim().toLowerCase();

    return convRepo.findBySessionIdAndProduct(sid, p)
        .map(c -> {
          c.touch();
          return convRepo.save(c);
        })
        .orElseGet(() -> convRepo.save(
            new ConversationEntity(UUID.randomUUID(), sid, p, Instant.now(), Instant.now())
        ));
  }

  @Transactional(readOnly = true)
  public Optional<ConversationEntity> findConversation(String sessionId, String product) {
    String sid = (sessionId == null) ? "" : sessionId.trim();
    String p = (product == null) ? "" : product.trim().toLowerCase();
    return convRepo.findBySessionIdAndProduct(sid, p);
  }

  @Transactional
  public void saveMessage(ConversationEntity conversation, String role, String content) {
    msgRepo.save(new MessageEntity(
        UUID.randomUUID(),
        conversation,
        role == null ? "" : role.trim(),
        content == null ? "" : content.trim(),
        Instant.now()
    ));
  }

  @Transactional(readOnly = true)
  public List<MessageEntity> getLastMessages(UUID conversationId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    List<MessageEntity> desc = msgRepo.findLastByConversationId(conversationId, safeLimit);
    Collections.reverse(desc); // viene desc, lo regresamos asc
    return desc;
  }

  /**
   * Actualiza summary cuando la conversación ya está grande.
   * No borra mensajes (eso lo dejamos para después).
   */
  @Transactional
  public ConversationEntity maybeSummarize(ConversationEntity conv, int hardLimitMsgs) {
    long total = msgRepo.countByConversation_Id(conv.getId());
    if (total < hardLimitMsgs) return conv;

    // opcional: evita resumir si ya resumiste hace poco (ej: 60s)
    Instant last = conv.getSummaryUpdatedAt();
    if (last != null && last.isAfter(Instant.now().minusSeconds(60))) {
      return conv;
    }

    // resumimos una ventana (últimos 40) para mantenerlo barato
    List<MessageEntity> lastMsgs = msgRepo.findLastByConversationId(conv.getId(), 40);
    Collections.reverse(lastMsgs);

    String transcript = buildTranscript(lastMsgs);

    String existing = conv.getSummary();
    String newSummary = openAi.summarize(conv.getProduct(), existing, transcript);

    conv.updateSummary(newSummary);
    return convRepo.save(conv);
  }

  private String buildTranscript(List<MessageEntity> msgsAsc) {
    if (msgsAsc == null || msgsAsc.isEmpty()) return "";

    StringBuilder sb = new StringBuilder();
    for (MessageEntity m : msgsAsc) {
      String role = m.getRole() == null ? "" : m.getRole().trim();
      String content = m.getContent() == null ? "" : m.getContent().trim();
      if (content.isBlank()) continue;

      // normaliza roles (por si cae "assistant"/"bot")
      String r = role.toLowerCase();
      if (!r.equals("user") && !r.equals("assistant") && !r.equals("system")) {
        r = "user";
      }

      sb.append(r).append(": ").append(content).append("\n");
    }
    return sb.toString().trim();
  }
}
