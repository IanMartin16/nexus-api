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
      @Value("${openai.base-url}") String baseUrl,
      @Value("${openai.api-key}") String apiKey,
      @Value("${openai.model}") String model,
      @Value("${openai.max-output-tokens}") int maxOutputTokens,
      @Value("${openai.timeout-ms}") long timeoutMs
  ) {
    this.mapper = mapper;
    this.model = model;
    this.maxOutputTokens = maxOutputTokens;
    this.timeout = Duration.ofMillis(timeoutMs);

    this.webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  public String generateAnswerWithContext(String instructions, String context, String userMessage) {
    String input = buildInput(context, userMessage);

    Map<String, Object> body = new HashMap<>();
    body.put("model", model);
    body.put("instructions", instructions);
    body.put("input", input);
    body.put("max_output_tokens", maxOutputTokens);

    return callResponses(body);
  }

  public String summarize(String product, String existingSummary, String transcript) {
    String p = safe(product);
    String existing = safe(existingSummary);
    String tr = safe(transcript);

    // recorte simple para evitar prompts gigantes
    if (tr.length() > 9000) tr = tr.substring(0, 9000);

    String instructions = """
  Eres un sistema que resume conversaciones para un chatbot.

  OBJETIVO:
  - Actualiza un resumen acumulado corto y útil del chat.
  - Producto: %s

  REGLAS:
  - Responde SOLO con el resumen actualizado (sin títulos, sin markdown).
  - 8 a 14 bullets máximo, ultra concreto.
  - Incluye: contexto del usuario, estado del setup, decisiones, URLs importantes (sin secretos), pendientes y próximos pasos.
  - Si ya hay resumen previo, intégralo y mejora (no lo repitas literal).
  - No incluyas secretos (API keys, passwords, tokens). Si aparecen, omítelos.
  """.formatted(p);
  
    String input = """
  RESUMEN PREVIO:
  %s

  TRANSCRIPCIÓN RECIENTE:
  %s
  """.formatted(existing.isBlank() ? "(vacío)" : existing, tr);

    Map<String, Object> body = new HashMap<>();
    body.put("model", model);
    body.put("instructions", instructions);
    body.put("input", input);
    body.put("max_output_tokens", Math.min(700, maxOutputTokens));

    return callResponses(body);
  }

  private String buildInput(String context, String userMessage) {
    String c = (context == null ? "" : context.trim());
    String u = (userMessage == null ? "" : userMessage.trim());

    if (c.isBlank()) return u;

    return """
CONTEXTO:
%s

USUARIO:
%s
""".formatted(c, u);
  }

  private String callResponses(Map<String, Object> body) {
    try {
      String json = webClient.post()
          .uri("/responses")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(timeout)
          .block();

      if (json == null || json.isBlank()) return "No recibí respuesta del modelo.";

      JsonNode root = mapper.readTree(json);

      // output -> message -> content -> output_text
      JsonNode output = root.path("output");
      if (output.isArray()) {
        for (JsonNode item : output) {
          if ("message".equals(item.path("type").asText())) {
            JsonNode content = item.path("content");
            if (content.isArray()) {
              for (JsonNode c : content) {
                if ("output_text".equals(c.path("type").asText())) {
                  String text = c.path("text").asText();
                  if (text != null && !text.isBlank()) return text.trim();
                }
              }
            }
          }
        }
      }

      // fallback: a veces viene output_text directo
      String fallback = root.path("output_text").asText();
      if (fallback != null && !fallback.isBlank()) return fallback.trim();

      return "No pude extraer output_text del response.";

    } catch (WebClientResponseException e) {
      return "OpenAI error " + e.getStatusCode().value() + ": " + safeBody(e.getResponseBodyAsString());
    } catch (Exception e) {
      return "Error llamando a OpenAI: " + e.getMessage();
    }
  }

  private String safeBody(String s) {
    if (s == null) return "";
    return s.length() > 800 ? s.substring(0, 800) + "..." : s;
  }

  private String safe(String s) {
    if (s == null) return "";
    String t = s.trim();
    return t.length() > 9000 ? t.substring(0, 9000) + "\n\n[...recortado...]" : t;
  }
}
