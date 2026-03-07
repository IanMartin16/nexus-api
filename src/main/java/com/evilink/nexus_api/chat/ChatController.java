package com.evilink.nexus_api.chat;

import com.evilink.nexus_api.docs.ProductDocsService;
import com.evilink.nexus_api.openai.OpenAiResponsesClient;
import com.evilink.nexus_api.persistence.MessageEntity;
import com.evilink.nexus_api.tools.cryptolink.CryptoLinkClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Set;
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
  private final CryptoLinkClient cryptoLink;


  public ChatController(
      @Value("${nexus.web-secret:}") String webSecret,
      OpenAiResponsesClient openAi,
      ProductDocsService docsService,
      ChatMemoryService memory,
      RateLimiterService rateLimiter,
      CryptoLinkClient cryptoLink
  ) {
    this.nexusWebSecret = webSecret;
    this.openAi = openAi;
    this.docsService = docsService;
    this.memory = memory;
    this.rateLimiter = rateLimiter;
    this.cryptoLink = cryptoLink;
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

  private static final Set<String> KNOWN_SYMBOLS = Set.of(
    "BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "AVAX", "DOT", "LINK", "BNB"
  );

  private List<String> extractSymbols(String message) {
    if (message == null || message.isBlank()) return List.of();

    String[] parts = message.toUpperCase().split("[^A-Z0-9]+");
    List<String> found = new ArrayList<>();

    for (String p : parts) {
      if (KNOWN_SYMBOLS.contains(p) && !found.contains(p)) {
        found.add(p);
      }
    }
    return found;
  }
  
  @PostMapping("/mcp/chat")
  public McpDtos.McpResponse chatMcp(
    @RequestHeader(value = "x-web-secret", required = false) String webSecret,
    @Valid @RequestBody ChatRequest req
  ) {
  // =========================
  // 0) AUTH
  // =========================
  if (nexusWebSecret != null && !nexusWebSecret.isBlank()) {
    if (webSecret == null || !webSecret.equals(nexusWebSecret)) {
      McpDtos.McpResponse r = new McpDtos.McpResponse();
      r.responseVersion = "0.1";
      r.traceId = "tr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
      r.answer = new McpDtos.McpResponse.Answer();
      r.answer.format = "rich";
      r.answer.summary = "Unauthorized";
      r.answer.sections = List.of(
          notice("sec_notice_unauth", "error", "Unauthorized", null)
      );
      return r;
    }
  }

  // =========================
  // 1) RATE LIMIT
  // =========================
  if (!rateLimiter.allow(req.sessionId(), req.product())) {
    McpDtos.McpResponse r = baseMcp();
    r.answer.summary = "Rate limit";
    r.answer.sections = List.of(
        notice("sec_notice_rl", "warning", "Rate limit: intenta de nuevo en 1 minuto", null)
    );
    return r;
  }

  String msg = (req.message() == null) ? "" : req.message().toLowerCase();
  String product = req.product().trim().toLowerCase();

  // =========================
  // 2) PRICES TOOL
  // =========================
  List<String> symbols = extractSymbols(req.message());
  boolean wantsPrices =
      !symbols.isEmpty() &&
      (
        msg.contains("precio") || 
        msg.contains("price") || 
        msg.contains("cotiza") || 
        msg.contains("vale") || 
        msg.contains("valor") || 
        msg.contains("cuánto") ||
        msg.contains("cuanto"));

  if (wantsPrices) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_prices_1";
    tc.tool = "cryptolink.prices.get";
    tc.input = Map.of("symbols", symbols, "fiat", "MXN");
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getPrices(symbols, "MXN");
    long ms = System.currentTimeMillis() - t0;
    Map<String, Object> sparkResp = null;
      try {
        sparkResp = cryptoLink.getPriceSpark(symbols, "MXN");
      } catch (Exception ignored) {
      // si falla spark, no se rompe prices
      }

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_prices_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error = "prices_not_available: " +  String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener precios";
      r.answer.sections = List.of(
          notice("sec_notice_prices_fail", "warning", "Precios no disponibles por ahora.", String.valueOf(resp))
      );
      return r;
    }

    String priceFiat = String.valueOf(resp.get("fiat"));
    String priceSource = String.valueOf(resp.get("source"));
    String priceAsOf = String.valueOf(resp.get("ts"));

    @SuppressWarnings("unchecked")
    Map<String, Object> priceMap = (Map<String, Object>) resp.get("prices");

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_prices_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = priceSource;
    tr.asOf = priceAsOf;
    r.toolResults = List.of(tr);

    @SuppressWarnings("unchecked")
    Map<String, Object> series =
        (sparkResp != null && Boolean.TRUE.equals(sparkResp.get("ok")))
            ? (Map<String, Object>) sparkResp.get("series")
            : Map.of();

    McpDtos.McpResponse.Section spark = new McpDtos.McpResponse.Section();
      spark.id = "sec_spark_prices";
      spark.type = "sparkline";
      spark.title = "Tendencia reciente";
      spark.items = symbols.stream()
        .map(sym -> Map.<String, Object>of(
          "label", sym,
          "points", series.get(sym) == null ? List.of() : series.get(sym)
      ))
      .toList();        

    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_prices";
    kpis.type = "kpi_grid";
    kpis.title = symbols.size() == 1 ? "Price Lookup" : "Price Snapshot";
    kpis.items = symbols.stream()
        .map(sym -> Map.<String, Object>of(
            "label", sym,
            "value", fmtMoney(priceMap.get(sym)),
            "unit", priceFiat
        ))
        .toList();

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_prices";
    sec.type = "text";
    sec.title = "Precios";
    sec.text = symbols.stream()
        .map(sym -> sym + " cotiza en **" + fmtMoney(priceMap.get(sym)) + " " + priceFiat + "**.")
        .reduce((a, b) -> a + "\n" + b)
        .orElse("Sin datos.");

    r.answer.summary = symbols.size() == 1
        ? "Precio de " + symbols.get(0)
        : "Precios consultados";

    r.answer.sections = List.of(
        notice(
            "sec_notice_prices",
            "info",
            "Precios obtenidos desde CryptoLink.",
            "asOf=" + shortIso(priceAsOf) + " · source=" + priceSource
        ),
        kpis,
        spark,
        sec
    );

    return r;
  }

  // =========================
  // 3) SNAPSHOT TOOL
  // =========================
  boolean wantsSnapshot =
      msg.contains("snapshot") ||
      msg.contains("mood") ||
      msg.contains("mercado") ||
      msg.contains("kpi") ||
      msg.contains("overview") ||
      msg.contains("resumen") ||
      (msg.contains("btc") && (msg.contains("hoy") || msg.contains("precio")));

  if (wantsSnapshot) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_snapshot_1";
    tc.tool = "cryptolink.snapshot.get";
    tc.input = Map.of("fiat", "USD");
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getSnapshot();
    long ms = System.currentTimeMillis() - t0;

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_snapshot_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error =  "snapshot_not_available" + String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener snapshot";
      r.answer.sections = List.of(
          notice("sec_notice_snap_fail", "warning", "Snapshot no disponible por ahora.", String.valueOf(resp))
      );
      return r;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> snap = (Map<String, Object>) resp.get("snapshot");

    String asOf = String.valueOf(snap.get("asOf"));
    String provider = String.valueOf(snap.get("provider"));
    String source = String.valueOf(snap.get("source"));
    String fiat = String.valueOf(snap.get("fiat"));
    String mood = String.valueOf(snap.get("marketMood"));

    @SuppressWarnings("unchecked")
    Map<String, Object> prices = (Map<String, Object>) snap.get("prices");

    Object btc = prices != null ? prices.get("BTC") : null;
    Object eth = prices != null ? prices.get("ETH") : null;

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_snapshot_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = source;
    tr.provider = provider;
    tr.asOf = asOf;
    r.toolResults = List.of(tr);

    r.answer.summary = "Snapshot CryptoLink (" + mood + ")";

    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_snapshot";
    kpis.type = "kpi_grid";
    kpis.title = "Market Snapshot";
    kpis.items = List.of(
        Map.of("label", "Mood", "value", mood),
        Map.of("label", "BTC", "value", fmtMoney(btc), "unit", fiat),
        Map.of("label", "ETH", "value", fmtMoney(eth), "unit", fiat)
    );

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_snapshot";
    sec.type = "text";
    sec.title = "Snapshot";
    sec.text =
        "Mercado en estado **" + mood + "**.\n" +
        "BTC cotiza en **" + fmtMoney(btc) + " " + fiat + "** y ETH en **" + fmtMoney(eth) + " " + fiat + "**.";

    r.answer.sections = List.of(
        notice(
            "sec_notice_snapshot",
            "info",
            "Snapshot obtenido desde CryptoLink.",
            "asOf=" + shortIso(asOf) + " · source=" + source + " · provider=" + provider
        ),
        kpis,
        sec
    );

    return r;
  }


  // =========================
  // 4) FALLBACK LLM
  // =========================
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

  McpDtos.McpResponse r = baseMcp();
  r.answer.summary = answer.length() > 140 ? answer.substring(0, 140) + "…" : answer;

  McpDtos.McpResponse.Section s = new McpDtos.McpResponse.Section();
  s.id = "sec_text_1";
  s.type = "text";
  s.title = "Respuesta";
  s.text = answer;

  r.answer.sections = List.of(
      notice("sec_notice_1", "info", "MCP v0.1 activo.", "conversationId=" + conversationId),
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
  private String fmtMoney(Object n) {
      try {
      double d = Double.parseDouble(String.valueOf(n));
      return String.format("%,.2f", d);
    } catch (Exception e) {
      return String.valueOf(n);
    }
  }

    private String shortIso(String iso) {
      if (iso == null) return "";
      return iso.length() >= 16 ? 
      iso.substring(0, 16).replace("T", " ") + " UTC" 
      : iso;
  }
  
}