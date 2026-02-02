package com.evilink.nexus_api.chat;

import com.evilink.nexus_api.docs.ProductDocsService;
import com.evilink.nexus_api.openai.OpenAiResponsesClient;
import com.evilink.nexus_api.persistence.MessageEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class ChatController {

  private final String nexusWebSecret;
  private final OpenAiResponsesClient openAi;
  private final ProductDocsService docsService;
  private final ChatMemoryService memory;
  private final RateLimiterService rateLimiter;


  public ChatController(
      @Value("${nexus.web-secret:}") String webSecret,
      OpenAiResponsesClient openAi,
      ProductDocsService docsService,
      ChatMemoryService memory,
      RateLimiterService rateLimiter
  ) {
    this.nexusWebSecret = webSecret;
    this.openAi = openAi;
    this.docsService = docsService;
    this.memory = memory;
    this.rateLimiter = rateLimiter;
  }

  @PostMapping("/chat")
  public Map<String, Object> chat(
      @RequestHeader(value = "x-web-secret", required = false) String webSecret,
      @Valid @RequestBody ChatRequest req
  ) {
    // auth opcional por secret
    if (nexusWebSecret != null && !nexusWebSecret.isBlank()) {
      if (webSecret == null || !webSecret.equals(nexusWebSecret)) {
        return Map.of("ok", false, "error", "Unauthorized");
      }
    }
    if (!rateLimiter.allow(req.sessionId(), req.product())) {
      return Map.of("ok", false, "error", "Rate limit: intenta de nuevo en 1 minuto");
    }


    String product = req.product().trim().toLowerCase();

    // 1) upsert conversación
    var conv = memory.getOrCreateConversation(req.sessionId(), product);
    UUID conversationId = conv.getId();

    // 2) (opcional) resumen si ya creció (antes de generar respuesta)
    conv = memory.maybeSummarize(conv, 40);

    // 3) docs del producto + recorte
    String docs = docsService.trim(docsService.getDocs(product), 14000);

    // 4) instrucciones
    String instructions = buildInstructions(product, docs);

    // 5) contexto: últimos N mensajes (antes de guardar el mensaje actual)
    //    así evitamos duplicar: el mensaje actual va como "input" aparte
    List<MessageEntity> last = memory.getLastMessages(conversationId, 12);
    String context = buildContextWithSummary(conv.getSummary(), last);

    // 6) guarda mensaje user
    memory.saveMessage(conv, "user", req.message());

    // 7) llamar OpenAI (input = mensaje actual)
    String answer = openAi.generateAnswerWithContext(instructions, context, req.message());

    // 8) guarda respuesta assistant
    memory.saveMessage(conv, "assistant", answer);

    return Map.of(
        "ok", true,
        "product", product,
        "answer", answer,
        "conversationId", conversationId.toString()
    );
  }

  @GetMapping("/history")
  public Map<String, Object> history(
      @RequestParam String sessionId,
      @RequestParam String product,
      @RequestParam(defaultValue = "30") int limit
  ) {
    String p = (product == null) ? "" : product.trim().toLowerCase();

    var convOpt = memory.findConversation(sessionId, p);
    if (convOpt.isEmpty()) {
      return Map.of("ok", true, "messages", List.of());
    }

    var conv = convOpt.get();
    var msgs = memory.getLastMessages(conv.getId(), limit);

    var out = msgs.stream().map(m -> Map.of(
        "id", m.getId().toString(),
        "role", m.getRole(),
        "text", m.getContent(),
        "ts", m.getCreatedAt().toString()
    )).toList();

    return Map.of("ok", true, "messages", out);
  }

  private String buildInstructions(String product, String docs) {
    boolean isGeneral = "evilink".equals(product) || "general".equals(product);

    String focusRule = isGeneral
        ? "- Puedes responder sobre Curpify, CryptoLink y evi_link.\n"
        : "- Enfócate SOLO en el producto: %s.\n".formatted(product);
      return """

Eres Nexus, el chatbot oficial de evi_link.

ESTILO:
- Responde en español, claro, práctico y directo.
- Usa bullets y ejemplos cortos cuando ayude.
- Si falta info, pregunta lo mínimo (1-2 preguntas máximo).

REGLAS:
- Enfócate SOLO en el producto: %s.
- Si preguntan algo fuera del producto, redirígelo al producto con amabilidad.
- No pidas ni aceptes datos sensibles (tarjetas, passwords, tokens, llaves).
- Si el usuario pega secretos, dile que los rote y los quite del chat.

DOCUMENTACIÓN DEL PRODUCTO (fuente principal):
%s
""".formatted(focusRule, (docs == null ? "" : docs));
  }

  private String buildContextWithSummary(String summary, List<MessageEntity> msgsAsc) {
    String recent = buildContext(msgsAsc);
    if (summary == null || summary.isBlank()) return recent;

    if (recent == null || recent.isBlank()) {
      return "RESUMEN ACUMULADO:\n" + summary.trim();
    }

    return """
RESUMEN ACUMULADO:
%s

CHAT RECIENTE:
%s
""".formatted(summary.trim(), recent);
  }

  private String buildContext(List<MessageEntity> msgsAsc) {
    if (msgsAsc == null || msgsAsc.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (MessageEntity m : msgsAsc) {
      String role = m.getRole() == null ? "" : m.getRole().trim();
      String content = m.getContent() == null ? "" : m.getContent().trim();
      if (content.isBlank()) continue;
      sb.append(role).append(": ").append(content).append("\n");
    }
    return sb.toString().trim();
  }

  public record ChatRequest(
      @NotBlank String sessionId,
      @NotBlank String product,
      @NotBlank String message
  ) {}
}
