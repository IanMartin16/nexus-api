package com.evilink.nexus_api.chat;

import com.evilink.nexus_api.docs.ProductDocsService;
import com.evilink.nexus_api.openai.OpenAiResponsesClient;
import com.evilink.nexus_api.persistence.MessageEntity;
import com.evilink.nexus_api.chat.McpDtos;
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
  
  @PostMapping("/mcp/chat")
  public McpDtos.McpResponse chatMcp(
      @RequestHeader(value = "x-web-secret", required = false) String webSecret,
      @Valid @RequestBody ChatRequest req
  ) {
    // ✅ reutiliza TODO tu flujo actual
    // (copiamos lo mínimo del método chat() y al final armamos MCPResponse)

    if (nexusWebSecret != null && !nexusWebSecret.isBlank()) {
      if (webSecret == null || !webSecret.equals(nexusWebSecret)) {
        McpDtos.McpResponse r = new McpDtos.McpResponse();
        r.responseVersion = "0.1";
        r.traceId = "tr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        r.answer = new McpDtos.McpResponse.Answer();
        r.answer.format = "rich";
        r.answer.summary = "Unauthorized";
        r.answer.sections = List.of(notice("sec_notice_unauth", "error", "Unauthorized", null));
        return r;
      }
    }

    if (!rateLimiter.allow(req.sessionId(), req.product())) {
      McpDtos.McpResponse r = baseMcp();
      r.answer.summary = "Rate limit";
      r.answer.sections = List.of(notice("sec_notice_rl", "warning", "Rate limit: intenta de nuevo en 1 minuto", null));
      return r;
    }

    String product = req.product().trim().toLowerCase();

    var conv = memory.getOrCreateConversation(req.sessionId(), product);
    var conversationId = conv.getId();

    conv = memory.maybeSummarize(conv, 40);

    String docs = docsService.trim(docsService.getDocs(product), 14000);
    String instructions = buildInstructions(product, docs);

    var last = memory.getLastMessages(conversationId, 12);
    String context = buildContextWithSummary(conv.getSummary(), last);

    memory.saveMessage(conv, "user", req.message());

    String answer = openAi.generateAnswerWithContext(instructions, context, req.message());

    memory.saveMessage(conv, "assistant", answer);

    // ✅ MCP response (v0.1): por ahora “text section”
    McpDtos.McpResponse r = baseMcp();
    r.answer.summary = answer.length() > 140 ? answer.substring(0, 140) + "…" : answer;

    McpDtos.McpResponse.Section s = new McpDtos.McpResponse.Section();
    s.id = "sec_text_1";
    s.type = "text";
    s.title = "Respuesta";
    s.text = answer;

    r.answer.sections = List.of(
        notice("sec_notice_1", "info", "MCP v0.1 activo (por ahora render text).", "conversationId=" + conversationId),
        s
    );

    return r;
  }

  private McpDtos.McpResponse baseMcp() {
    McpDtos.McpResponse r = new McpDtos.McpResponse();
    r.responseVersion = "0.1";
    r.traceId = "tr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    r.answer = new McpDtos.McpResponse.Answer();
    r.answer.format = "rich";
    r.answer.summary = "";
    r.answer.sections = List.of();
    return r;
  }

  private McpDtos.McpResponse.Section notice(String id, String kind, String message, String details) {
    McpDtos.McpResponse.Section n = new McpDtos.McpResponse.Section();
    n.id = id;
    n.type = "notice";
    n.kind = kind;
    n.message = message;
    n.details = details;
    return n;
  }
}
