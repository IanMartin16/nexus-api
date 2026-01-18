package com.evilink.nexus_api.chat;

import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ChatController {

  private final String nexusWebSecret;
  private final com.evilink.nexus_api.openai.OpenAiResponsesClient openAi;

  public ChatController(
      @Value("${nexus.web-secret:}") String webSecret,
      com.evilink.nexus_api.openai.OpenAiResponsesClient openAi
  ) {
    this.nexusWebSecret = webSecret;
    this.openAi = openAi;
  }

  @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> chat(
      @RequestHeader(value = "x-web-secret", required = false) String webSecret,
      @RequestBody ChatRequest req
  ) {
    // Seguridad simple por secret
    if (nexusWebSecret != null && !nexusWebSecret.isBlank()) {
      if (webSecret == null || !webSecret.equals(nexusWebSecret)) {
        return Map.of("ok", false, "error", "Unauthorized");
      }
    }

    String context = loadProductKnowledge(req.product());
    String instructions = buildInstructions(req.product(), context);

    String answer = openAi.generateAnswer(instructions, req.message());

    return Map.of(
        "ok", true,
        "product", req.product(),
        "answer", answer,
        "ts", Instant.now().toString()
    );
  }

  private String loadProductKnowledge(String product) {
    // Mapea productos a docs locales (temporal)
    String file = switch (product == null ? "" : product.trim().toLowerCase()) {
      case "curpify" -> "knowledge/curpify.md";
      case "cryptolink" -> "knowledge/cryptolink.md";
      case "evilink", "evi_link", "evi-link" -> "knowledge/evilink.md";
      default -> null;
    };

    if (file == null) return "";

    try {
      ClassPathResource r = new ClassPathResource(file);
      if (!r.exists()) return "";
      byte[] bytes = StreamUtils.copyToByteArray(r.getInputStream());
      String text = new String(bytes, StandardCharsets.UTF_8);

      // Evita meter docs gigantes (MVP)
      int max = 10_000;
      return text.length() > max ? text.substring(0, max) : text;

    } catch (Exception e) {
      return "";
    }
  }

  private String buildInstructions(String product, String context) {
    // Instrucciones fuertes anti-alucinación
    return """
Eres Nexus, el chatbot oficial de evi_link.
Responde SIEMPRE en español, claro y práctico.

REGLAS IMPORTANTES:
- Usa únicamente la información del CONTEXTO (documentación del producto).
- Si algo NO está en el CONTEXTO, dilo explícitamente y pide el dato mínimo.
- No inventes URLs, endpoints, headers, precios ni límites.
- Si hay un endpoint, repite exactamente el path y el header correcto.

PRODUCTO: %s

CONTEXTO (documentación):
%s
""".formatted(product, context == null ? "" : context);
  }

  public record ChatRequest(
      @NotBlank String sessionId,
      @NotBlank String product,
      @NotBlank String message
  ) {}
}
