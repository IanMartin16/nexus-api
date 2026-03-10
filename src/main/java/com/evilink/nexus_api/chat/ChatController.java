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
    sec.title = "Resumen";
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
  // 3) MOVERS
  // =========================

  List<String> moverSymbols = extractSymbols(req.message());
  if (moverSymbols.isEmpty()) {
    moverSymbols = List.of("BTC", "ETH", "SOL");
  }

  boolean wantsMovers =
      msg.contains("movers") ||
      msg.contains("top movers") ||
      msg.contains("gainers") ||
      msg.contains("losers") ||
      msg.contains("que subio mas") ||
      msg.contains("qué subió más") ||
      msg.contains("que bajo mas") ||
      msg.contains("qué bajó más") ||
      msg.contains("que se movio mas") ||
      msg.contains("qué se movió más");

  if (wantsMovers) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_movers_1";
    tc.tool = "cryptolink.movers.get";
    tc.input = Map.of(
        "symbols", moverSymbols,
        "fiat", "MXN",
        "limit", 3
    );
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getMovers(moverSymbols, "MXN", 3);
    long ms = System.currentTimeMillis() - t0;

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_movers_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error = "movers_not_available: " + String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener movers";
      r.answer.sections = List.of(
          notice("sec_notice_movers_fail", "warning", "Movers no disponibles por ahora.", String.valueOf(resp))
      );
      return r;
    }

    String moverFiat = String.valueOf(resp.get("fiat"));
    String moverSource = String.valueOf(resp.get("source"));
    String moverAsOf = String.valueOf(resp.get("ts"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> gainers = (List<Map<String, Object>>) resp.get("gainers");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> losers = (List<Map<String, Object>>) resp.get("losers");

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_movers_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = moverSource;
    tr.asOf = moverAsOf;
    r.toolResults = List.of(tr);

    // KPIs: top gainer + top loser
    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_movers";
    kpis.type = "kpi_grid";
    kpis.title = "Top Movers";

    List<Map<String, Object>> moverItems = new java.util.ArrayList<>();

    if (gainers != null && !gainers.isEmpty()) {
      Map<String, Object> g = gainers.get(0);
      moverItems.add(Map.of(
          "label", "Top Gainer",
          "value", String.valueOf(g.get("symbol")),
          "unit", fmtPct(g.get("changePct")),
          "tone", "up"
      ));
    }

    if (losers != null && !losers.isEmpty()) {
      Map<String, Object> l = losers.get(0);
      moverItems.add(Map.of(
          "label", "Top Loser",
          "value", String.valueOf(l.get("symbol")),
          "unit", fmtPct(l.get("changePct")),
          "tone", "down"
      ));
    }

    if (moverItems.isEmpty()) {
      moverItems.add(Map.of(
          "label", "Estado",
          "value", "Sin movers claros",
          "unit", ""
      ));
    }

    kpis.items = moverItems;

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_movers";
    sec.type = "text";
    sec.title = "Resumen de movers";

    String gainersText = (gainers == null || gainers.isEmpty())
        ? "No hay gainers claros por ahora."
        : gainers.stream()
            .map(row -> {
              String symbol = String.valueOf(row.get("symbol"));
              String pct = fmtPct(row.get("changePct"));
              Object last = row.get("last");
              return symbol + " lidera al alza con **" + pct + "** y último precio de **" + fmtMoney(last) + " " + moverFiat + "**.";
            })
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

    String losersText = (losers == null || losers.isEmpty())
        ? "No hay losers claros por ahora."
        : losers.stream()
            .map(row -> {
              String symbol = String.valueOf(row.get("symbol"));
              String pct = fmtPct(row.get("changePct"));
              Object last = row.get("last");
              return symbol + " lidera a la baja con **" + pct + "** y último precio de **" + fmtMoney(last) + " " + moverFiat + "**.";
            })
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

    sec.text = "**Al Alza**\n" + gainersText + "\n\n" + "**A la Baja**\n" + losersText;

    r.answer.summary = "Top movers del mercado";
    r.answer.sections = List.of(
        notice(
            "sec_notice_movers",
            "info",
            "Movers obtenidos desde CryptoLink.",
            "asOf=" + shortIso(moverAsOf) + " · source=" + moverSource
        ),
        kpis,
        sec
    );

    return r;
  }    

    // =========================
    // 4) MOMENTUM
    // =========================

  List<String> momentumSymbols = extractSymbols(req.message());
  if (momentumSymbols.isEmpty()) {
    momentumSymbols = List.of("BTC", "ETH", "SOL");
  }

  boolean wantsMomentum =
      msg.contains("momentum") ||
      msg.contains("fuerza") ||
      msg.contains("strength") ||
      msg.contains("traccion") ||
      msg.contains("tracción") ||
      msg.contains("consistencia");

  if (wantsMomentum) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_momentum_1";
    tc.tool = "cryptolink.momentum.get";
    tc.input = Map.of(
        "symbols", momentumSymbols,
        "fiat", "MXN"
    );
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getMomentum(momentumSymbols, "MXN");
    long ms = System.currentTimeMillis() - t0;

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_momentum_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error = "momentum_not_available: " + String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener momentum";
      r.answer.sections = List.of(
          notice("sec_notice_momentum_fail", "warning", "Momentum no disponible por ahora.", String.valueOf(resp))
      );
      return r;
    }

    String momentumFiat = String.valueOf(resp.get("fiat"));
    String momentumSource = String.valueOf(resp.get("source"));
    String momentumAsOf = String.valueOf(resp.get("ts"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> momentum = (List<Map<String, Object>>) resp.get("momentum");

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_momentum_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = momentumSource;
    tr.asOf = momentumAsOf;
    r.toolResults = List.of(tr);

    List<Map<String, Object>> validMomentum = momentum == null
        ? List.of()
        : momentum.stream()
            .filter(row -> !"insufficient-history".equals(String.valueOf(row.get("source"))))
            .toList();

    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_momentum";
    kpis.type = "kpi_grid";
    kpis.title = "Momentum";

    if (!validMomentum.isEmpty()) {
      kpis.items = validMomentum.stream()
          .limit(3)
          .map(row -> {
            String direction = String.valueOf(row.get("direction"));
            String tone =
                "up".equals(direction) ? "up" :
                "down".equals(direction) ? "down" :
                "neutral";

            return Map.<String, Object>of(
                "label", String.valueOf(row.get("symbol")),
                "value", String.valueOf(row.get("strength")).toUpperCase(),
                "unit", fmtPct(row.get("changePct")),
                "tone", tone
            );
          })
          .toList();
    } else {
      kpis.items = List.of(
          Map.of(
              "label", "Estado",
              "value", "Madurando",
              "unit", "sin histórico suficiente"
          )
      );
    }

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_momentum";
    sec.type = "text";
    sec.title = "Momentum";

    if (!validMomentum.isEmpty()) {
      String useful = validMomentum.stream()
          .map(row -> {
            String symbol = String.valueOf(row.get("symbol"));
            String direction = String.valueOf(row.get("direction"));
            String strength = String.valueOf(row.get("strength"));
            String pct = fmtPct(row.get("changePct"));
            Object last = row.get("last");
            return symbol + " muestra momentum **" + esDirection(direction) + "** con fuerza **" + esStrength(strength) + "**, variación de **" + pct + "** y último precio de **" + fmtMoney(last) + " " + momentumFiat + "**.";
          })
          .reduce((a, b) -> a + "\n" + b)
          .orElse("Sin datos.");
    
      long insufficientCount = momentum.stream()
          .filter(row -> "insufficient-history".equals(String.valueOf(row.get("source"))))
          .count();

      if (insufficientCount > 0) {
        useful += "\n\nAlgunos activos aún no tienen suficiente histórico para evaluar momentum con confianza.";
      }

      sec.text = useful;
    } else {
      sec.text = "Momentum aún en formación. Se necesita más histórico para evaluar fuerza y consistencia del movimiento.";
    }

    r.answer.summary = "Momentum del mercado";
    r.answer.sections = List.of(
        notice(
            "sec_notice_momentum",
            "info",
            "Momentum obtenido desde CryptoLink.",
            "asOf=" + shortIso(momentumAsOf) + " · source=" + momentumSource
        ),
        kpis,
        sec
    );

    return r;
  }    

  // =========================
  // 5) REGIME
  // =========================

  List<String> regimeSymbols = extractSymbols(req.message());
    if (regimeSymbols.isEmpty()) {
    regimeSymbols = List.of("BTC", "ETH", "SOL");
  }

  boolean wantsRegime =
      msg.contains("regime") ||
      msg.contains("market regime") ||
      msg.contains("regimen") ||
      msg.contains("régimen") ||
      msg.contains("como esta el mercado") ||
      msg.contains("cómo está el mercado") ||
      msg.contains("sesgo del mercado") ||
      msg.contains("estado del mercado");

  if (wantsRegime) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_regime_1";
    tc.tool = "cryptolink.regime.get";
    tc.input = Map.of(
        "symbols", regimeSymbols,
        "fiat", "MXN"
    );
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getRegime(regimeSymbols, "MXN");
    long ms = System.currentTimeMillis() - t0;

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_regime_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error = "regime_not_available: " + String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener el régimen del mercado";
      r.answer.sections = List.of(
          notice("sec_notice_regime_fail", "warning", "Régimen no disponible por ahora.", String.valueOf(resp))
      );
      return r;
    }

    String regimeFiat = String.valueOf(resp.get("fiat"));
    String regimeSource = String.valueOf(resp.get("source"));
    String regimeAsOf = String.valueOf(resp.get("ts"));

    @SuppressWarnings("unchecked")
    Map<String, Object> regime = (Map<String, Object>) resp.get("regime");

    String state = String.valueOf(regime.get("state"));
    Object score = regime.get("score");
    Object confidence = regime.get("confidence");
    String summary = String.valueOf(regime.get("summary"));

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_regime_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = regimeSource;
    tr.asOf = regimeAsOf;
    r.toolResults = List.of(tr);

    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_regime";
    kpis.type = "kpi_grid";
    kpis.title = "Régimen del mercado";
    kpis.items = List.of(
        Map.<String, Object>of(
            "label", "Estado",
            "value", esRegimeState(state),
            "unit", "",
            "tone", regimeTone(state)
        ),
        Map.<String, Object>of(
            "label", "Confianza",
            "value", fmtConfidence(confidence),
            "unit", "",
            "tone", regimeTone(state)
        ),
        Map.<String, Object>of(
            "label", "Score",
            "value", String.valueOf(score),
            "unit", "",
            "tone", regimeTone(state)
        )
    );

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_regime";
    sec.type = "text";
    sec.title = "Lectura del mercado";
    sec.text =
        "El mercado muestra un régimen **" + esRegimeState(state) + "** con confianza de **" +
        fmtConfidence(confidence) + "** y score agregado de **" + score + "**.\n\n" +
        summary;

    r.answer.summary = "Régimen del mercado";
    r.answer.sections = List.of(
        notice(
            "sec_notice_regime",
            "info",
            "Régimen obtenido desde CryptoLink.",
            "asOf=" + shortIso(regimeAsOf) + " · source=" + regimeSource + " · fiat=" + regimeFiat
        ),
        kpis,
        sec
    );

    return r;
  }    

  // =========================
  // 6) TRENDS
  // =========================

  List<String> trendSymbols = extractSymbols(req.message());
  if (trendSymbols.isEmpty()) {
    trendSymbols = List.of("BTC", "ETH", "SOL");
  }

  boolean wantsTrends =
      msg.contains("trend") ||
      msg.contains("trends") ||
      msg.contains("tendencia") ||
      msg.contains("tendencias") ||
      msg.contains("movers") ||
      msg.contains("top movers") ||
      msg.contains("que subio") ||
      msg.contains("qué subió") ||
      msg.contains("que esta fuerte") ||
      msg.contains("qué está fuerte");

  if (wantsTrends) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_trends_1";
    tc.tool = "cryptolink.trends.get";
    tc.input = Map.of(
        "symbols", trendSymbols,
        "fiat", "MXN"
    );
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getTrends(trendSymbols, "MXN");
    long ms = System.currentTimeMillis() - t0;

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_trends_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error = "trends_not_available: " + String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener tendencias";
      r.answer.sections = List.of(
          notice("sec_notice_trends_fail", "warning", "Tendencias no disponibles por ahora.", String.valueOf(resp))
      );
      return r;
    }

    String trendFiat = String.valueOf(resp.get("fiat"));
    String trendSource = String.valueOf(resp.get("source"));
    String trendAsOf = String.valueOf(resp.get("ts"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> trends = (List<Map<String, Object>>) resp.get("trends");

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_trends_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = trendSource;
    tr.asOf = trendAsOf;
    r.toolResults = List.of(tr);

    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_trends";
    kpis.type = "kpi_grid";
    kpis.title = "Market Trends";
    kpis.items = trends.stream()
        .limit(3)
        .map(row -> Map.<String, Object>of(
            "label", String.valueOf(row.get("symbol")),
            "value", String.valueOf(row.get("direction")).toUpperCase(),
            "unit", fmtPct(row.get("changePct"))
        ))
        .toList();

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_trends";
    sec.type = "text";
    sec.title = "Tendencias";
    sec.text = trends.stream()
        .map(row -> {
          String symbol = String.valueOf(row.get("symbol"));
          String direction = String.valueOf(row.get("direction"));
          String changePct = fmtPct(row.get("changePct"));
          Object last = row.get("last");
          return symbol + " está **" + direction + "** con variación de **" + changePct + "** y último precio de **" + fmtMoney(last) + " " + trendFiat + "**.";
        })
        .reduce((a, b) -> a + "\n" + b)
        .orElse("Sin datos.");

    r.answer.summary = "Tendencias del mercado";

    r.answer.sections = List.of(
        notice(
            "sec_notice_trends",
            "info",
            "Tendencias obtenidas desde CryptoLink.",
            "asOf=" + shortIso(trendAsOf) + " · source=" + trendSource
        ),
        kpis,
        sec
    );

    return r;
  }

  // =========================
  // 7) RISK FLAGS
  // =========================

  List<String> riskSymbols = extractSymbols(req.message());
  if (riskSymbols.isEmpty()) {
    riskSymbols = List.of("BTC", "ETH", "SOL");
  }

  boolean wantsRiskFlags =
      msg.contains("risk flags") ||
      msg.contains("risk") ||
      msg.contains("riesgo") ||
      msg.contains("riesgos") ||
      msg.contains("alertas") ||
      msg.contains("banderas de riesgo") ||
      msg.contains("que riesgos ves") ||
      msg.contains("qué riesgos ves") ||
      msg.contains("como ves el riesgo") ||
      msg.contains("cómo ves el riesgo");

  if (wantsRiskFlags) {
    McpDtos.McpResponse r = baseMcp();

    McpDtos.McpResponse.ToolCall tc = new McpDtos.McpResponse.ToolCall();
    tc.id = "tc_risk_flags_1";
    tc.tool = "cryptolink.risk-flags.get";
    tc.input = Map.of(
        "symbols", riskSymbols,
        "fiat", "MXN"
    );
    r.toolCalls = List.of(tc);

    long t0 = System.currentTimeMillis();
    Map<String, Object> resp = cryptoLink.getRiskFlags(riskSymbols, "MXN");
    long ms = System.currentTimeMillis() - t0;

    if (resp == null || !Boolean.TRUE.equals(resp.get("ok"))) {
      McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
      tr.toolCallId = "tc_risk_flags_1";
      tr.ok = false;
      tr.latencyMs = ms;
      tr.error = "risk_flags_not_available: " + String.valueOf(resp);
      r.toolResults = List.of(tr);

      r.answer.summary = "No pude obtener banderas de riesgo";
      r.answer.sections = List.of(
        notice("sec_notice_risk_fail", "warning", "Risk flags no disponibles por ahora.", String.valueOf(resp))
      );
      return r;
    }

    String riskFiat = String.valueOf(resp.get("fiat"));
    String riskSource = String.valueOf(resp.get("source"));
    String riskAsOf = String.valueOf(resp.get("ts"));
    String riskSummary = String.valueOf(resp.get("summary"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> flags = (List<Map<String, Object>>) resp.get("flags");

    McpDtos.McpResponse.ToolResult tr = new McpDtos.McpResponse.ToolResult();
    tr.toolCallId = "tc_risk_flags_1";
    tr.ok = true;
    tr.latencyMs = ms;
    tr.source = riskSource;
    tr.asOf = riskAsOf;
    r.toolResults = List.of(tr);

    long totalFlags = flags == null ? 0 : flags.size();
    long mediumOrHigh = flags == null ? 0 : flags.stream()
        .filter(f -> {
          String sev = String.valueOf(f.get("severity"));
          return "medium".equalsIgnoreCase(sev) || "high".equalsIgnoreCase(sev);
        })
        .count();

    String dominantSeverity = "low";
    if (flags != null && !flags.isEmpty()) {
      if (flags.stream().anyMatch(f -> "high".equalsIgnoreCase(String.valueOf(f.get("severity"))))) {
        dominantSeverity = "high";
      } else if (flags.stream().anyMatch(f -> "medium".equalsIgnoreCase(String.valueOf(f.get("severity"))))) {
        dominantSeverity = "medium";
      }
    }

    McpDtos.McpResponse.Section kpis = new McpDtos.McpResponse.Section();
    kpis.id = "sec_kpis_risk";
    kpis.type = "kpi_grid";
    kpis.title = "Risk Flags";
    kpis.items = List.of(
        Map.<String, Object>of(
            "label", "Flags",
            "value", String.valueOf(totalFlags),
            "unit", "",
            "tone", severityTone(dominantSeverity)
        ),
        Map.<String, Object>of(
            "label", "Severidad",
            "value", esSeverity(dominantSeverity),
            "unit", "",
            "tone", severityTone(dominantSeverity)
        ),
        Map.<String, Object>of(
            "label", "Relevantes",
            "value", String.valueOf(mediumOrHigh),
            "unit", "",
            "tone", severityTone(dominantSeverity)
        )
    );

    McpDtos.McpResponse.Section sec = new McpDtos.McpResponse.Section();
    sec.id = "sec_text_risk";
    sec.type = "text";
    sec.title = "Lectura de riesgo";

    if (flags == null || flags.isEmpty()) {
      sec.text = riskSummary;
    } else {
      String detailText = flags.stream()
          .map(f -> {
            String title = String.valueOf(f.get("title"));
            String detail = String.valueOf(f.get("detail"));
            String sev = esSeverity(String.valueOf(f.get("severity")));
            return "- **" + title + "** (" + sev + "): " + detail;
          })
          .reduce((a, b) -> a + "\n" + b)
          .orElse("Sin alertas.");

      sec.text = riskSummary + "\n\n" + detailText;
    }

    r.answer.summary = "Banderas de riesgo del mercado";
    r.answer.sections = List.of(
        notice(
            "sec_notice_risk",
            "info",
            "Risk flags obtenidos desde CryptoLink.",
            "asOf=" + shortIso(riskAsOf) + " · source=" + riskSource + " · fiat=" + riskFiat
        ),
        kpis,
        sec
    );

    return r;
  }    

  // =========================
  // 8) SNAPSHOT TOOL
  // =========================
  boolean wantsSnapshot =
      msg.contains("snapshot") ||
      msg.contains("mood") ||
      msg.contains("market snapshot") ||
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
  // 9) FALLBACK LLM
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

  private String fmtPct(Object n) {
    try {
      double d = Double.parseDouble(String.valueOf(n));
      return String.format("%,.2f%%", d);
    } catch (Exception e) {
      return String.valueOf(n);
    }
  }

  private String esDirection(String direction) {
    if (direction == null) return "estable";
    return switch (direction.toLowerCase()) {
      case "up" -> "alcista";
      case "down" -> "bajista";
      default -> "estable";
    };
  }

  private String esStrength(String strength) {
    if (strength == null) return "bajo";
    return switch (strength.toLowerCase()) {
      case "high" -> "alto";
      case "medium" -> "medio";
      default -> "bajo";
    };
  }

  private String esRegimeState(String state) {
    if (state == null) return "estable";
   return switch (state.toLowerCase()) {
      case "bullish" -> "alcista";
      case "bearish" -> "bajista";
      case "mixed" -> "mixto";
      default -> "estable";
    };
  }

  private String regimeTone(String state) {
    if (state == null) return "neutral";
    return switch (state.toLowerCase()) {
      case "bullish" -> "up";
      case "bearish" -> "down";
      default -> "neutral";
    };
  }

  private String fmtConfidence(Object n) {
    try {
      double d = Double.parseDouble(String.valueOf(n)) * 100.0;
      return String.format("%,.0f%%", d);
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

  private String esSeverity(String severity) {
    if (severity == null) return "baja";
    return switch (severity.toLowerCase()) {
      case "high" -> "alta";
      case "medium" -> "media";
      default -> "baja";
    };
  }

  private String severityTone(String severity) {
    if (severity == null) return "neutral";
    return switch (severity.toLowerCase()) {
      case "high" -> "down";
      case "medium" -> "warning";
      default -> "neutral";
    };
  }
  
}