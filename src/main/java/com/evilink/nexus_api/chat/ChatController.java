package com.evilink.nexus_api.chat;

import com.evilink.nexus_api.openai.OpenAiResponsesClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ChatController {

  private final String nexusWebSecret;
  private final OpenAiResponsesClient openAiResponsesClient;

  public ChatController(
      @Value("${nexus.web-secret:}") String webSecret,
      OpenAiResponsesClient openAiResponsesClient
  ) {
    this.nexusWebSecret = webSecret;
    this.openAiResponsesClient = openAiResponsesClient;
  }

  @PostMapping("/chat")
  public Map<String, Object> chat(
      @RequestHeader(value = "x-web-secret", required = false) String webSecret,
      @Valid @RequestBody ChatRequest req
  ) {
    // Si configuraste un secreto, lo exigimos
    if (nexusWebSecret != null && !nexusWebSecret.isBlank()) {
      if (webSecret == null || !webSecret.equals(nexusWebSecret)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
      }
    }

    String instructions = buildInstructions(req.product());
    String answer = openAiResponsesClient.generateAnswer(instructions, req.message());

    return Map.of(
        "ok", true,
        "product", req.product(),
        "answer", answer
    );
  }

  private String buildInstructions(String product) {
    // Puedes afinar esto cuando metamos RAG por README
    return """
      Eres Nexus, el chatbot oficial de evi_link.
      Contesta en español, claro y práctico.
      Enfócate en el producto: %s.
      Si no tienes información suficiente, dilo y pide el dato mínimo necesario.
      """.formatted(product);
  }

  public record ChatRequest(
      @NotBlank String sessionId,
      @NotBlank String product,
      @NotBlank String message
  ) {}
}
