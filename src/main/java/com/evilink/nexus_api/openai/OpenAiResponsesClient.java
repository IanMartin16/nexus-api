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

    this.webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();

    this.timeout = Duration.ofMillis(timeoutMs);
  }

  private final Duration timeout;

  public String generateAnswer(String instructions, String userMessage) {
    // Responses API: POST /v1/responses, body con model + input + instructions :contentReference[oaicite:2]{index=2}
    Map<String, Object> body = new HashMap<>();
    body.put("model", model);
    body.put("input", userMessage);               // input puede ser string :contentReference[oaicite:3]{index=3}
    body.put("instructions", instructions);       // system/developer message :contentReference[oaicite:4]{index=4}
    body.put("max_output_tokens", maxOutputTokens);

    try {
      String json = webClient.post()
          .uri("/responses")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(String.class)
          .timeout(timeout)
          .block();

      if (json == null || json.isBlank()) return "No recibí respuesta del modelo.";

      // Extrae: output[0].content[0].text cuando type=output_text :contentReference[oaicite:5]{index=5}
      JsonNode root = mapper.readTree(json);
      JsonNode output = root.path("output");
      if (output.isArray()) {
        for (JsonNode item : output) {
          if ("message".equals(item.path("type").asText())) {
            JsonNode content = item.path("content");
            if (content.isArray()) {
              for (JsonNode c : content) {
                if ("output_text".equals(c.path("type").asText())) {
                  String text = c.path("text").asText();
                  if (text != null && !text.isBlank()) return text;
                }
              }
            }
          }
        }
      }

      return "No pude extraer output_text del response.";

    } catch (WebClientResponseException e) {
      // Esto te ayuda muchísimo a debuggear (401/429/500)
      return "OpenAI error " + e.getStatusCode().value() + ": " + safeBody(e.getResponseBodyAsString());
    } catch (Exception e) {
      return "Error llamando a OpenAI: " + e.getMessage();
    }
  }

  private String safeBody(String s) {
    if (s == null) return "";
    return s.length() > 600 ? s.substring(0, 600) + "..." : s;
  }
}
