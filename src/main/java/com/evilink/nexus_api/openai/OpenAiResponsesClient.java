package com.evilink.nexus_api.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class OpenAiResponsesClient {

  private final WebClient webClient;
  private final ObjectMapper mapper;

  private final String model;
  private final int maxOutputTokens;
  private final Duration timeout;

  public OpenAiResponsesClient(
      ObjectMapper mapper,
      @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
      @Value("${openai.api-key:}") String apiKey,
      @Value("${openai.model:gpt-4.1}") String model,
      @Value("${openai.max-output-tokens:700}") int maxOutputTokens,
      @Value("${openai.timeout-ms:20000}") long timeoutMs
  ) {
    this.mapper = mapper;
    this.model = model;
    this.maxOutputTokens = maxOutputTokens;
    this.timeout = Duration.ofMillis(timeoutMs);

    this.webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey == null ? "" : apiKey.trim()))
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  /**
   * Llamada simple: instructions + userMessage
   */
  public String generateAnswer(String instructions, String userMessage) {
    return callResponsesApi(instructions, userMessage);
  }

  /**
   * Llamada con contexto/historial en texto (MVP simple y efectivo).
   * context: algo como:
   * user: ...
   * assistant: ...
   */
  public String generateAnswerWithContext(String instructions, String context, String userMessage) {
    String input = buildInput(context, userMessage);
    return callResponsesApi(instructions, input);
  }

  private String buildInput(String context, String userMessage) {
    String msg = (userMessage == null) ? "" : userMessage.trim();
    String ctx = (context == null) ? "" : context.trim();

    if (ctx.isBlank()) return msg;

    return """
CONVERSACIÓN PREVIA (para mantener continuidad):
%s

MENSAJE ACTUAL DEL USUARIO:
%s
""".formatted(ctx, msg);
  }

  private String callResponsesApi(String instructions, String input) {
    Map<String, Object> body = new HashMap<>();
    body.put("model", model);
    body.put("instructions", safe(instructions));
    body.put("input", safe(input));
    body.put("max_output_tokens", maxOutputTokens);

    try {
      String json = webClient.post()
          .uri("/responses")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(timeout)
          .onErrorResume(e -> Mono.error(e))
          .block();

      if (json == null || json.isBlank()) {
        return "No recibí respuesta del modelo.";
      }

      return extractOutputText(json);

    } catch (WebClientResponseException e) {
      return "OpenAI error " + e.getStatusCode().value() + ": " + safeBody(e.getResponseBodyAsString());
    } catch (Exception e) {
      return "Error llamando a OpenAI: " + e.getMessage();
    }
  }

  /**
   * Extrae output_text de Responses API.
   * Busca en output[] -> content[] type=output_text -> text
   */
  private String extractOutputText(String json) throws Exception {
    JsonNode root = mapper.readTree(json);

    // Caso normal: output array
    JsonNode output = root.path("output");
    if (output.isArray()) {
      for (JsonNode item : output) {
        // típicamente item.type = "message"
        JsonNode content = item.path("content");
        if (content.isArray()) {
          for (JsonNode c : content) {
            if ("output_text".equals(c.path("type").asText())) {
              String text = c.path("text").asText("");
              if (!text.isBlank()) return text.trim();
            }
          }
        }
      }
    }

    // Fallbacks por si cambia la forma:
    // 1) text directo
    String directText = root.path("text").asText("");
    if (!directText.isBlank()) return directText.trim();

    // 2) output_text directo
    String outText = root.path("output_text").asText("");
    if (!outText.isBlank()) return outText.trim();

    return "No pude extraer output_text del response.";
  }

  private String safe(String s) {
    if (s == null) return "";
    return s;
  }

  private String safeBody(String s) {
    if (s == null) return "";
    String t = s.trim();
    return t.length() > 800 ? t.substring(0, 800) + "..." : t;
  }

  public String summarize(String product, String existingSummary, String transcript) {
    String instructions = """
  Eres un asistente que resume conversaciones.
  Objetivo: crear/actualizar un RESUMEN acumulado para el chatbot Nexus.
  Producto: %s

  REGLAS:
  - Resume en español.
  - Máximo 10 bullets (cortos).
  - Conserva: decisiones, datos técnicos relevantes, endpoints, headers, errores y soluciones.
  - No inventes datos.
  - Si ya hay resumen previo, actualízalo (no lo repitas completo).
  - NO incluyas secretos, llaves, tokens ni credenciales (si aparecen, omítelos).
  """.formatted(product);

    String input = """
  RESUMEN PREVIO (si existe):
  %s

  NUEVOS MENSAJES PARA INCORPORAR:
  %s
  """.formatted(existingSummary == null ? "" : existingSummary, transcript == null ? "" : transcript);

    return generateAnswer(instructions, input);
  }

}
