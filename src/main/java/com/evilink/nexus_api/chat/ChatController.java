package com.evilink.nexus_api.chat;

import com.evilink.nexus_api.docs.ProductDocsService;
import com.evilink.nexus_api.openai.OpenAiResponsesClient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ChatController {

  private final String nexusWebSecret;
  private final OpenAiResponsesClient openAiResponsesClient;
  private final ProductDocsService docsService;
  private static final Logger log = LoggerFactory.getLogger(ChatController.class);


  public ChatController(
      @Value("${nexus.web-secret:}") String webSecret,
      OpenAiResponsesClient openAiResponsesClient,
      ProductDocsService docsService
  ) {
    this.nexusWebSecret = webSecret;
    this.openAiResponsesClient = openAiResponsesClient;
    this.docsService = docsService;
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
    log.info("CHAT IN sessionId='{}' product='{}' msgLen={}",
        req.sessionId(), req.product(), 
        req.message() == null ? -1 : req.message().length()
    );

    String product = (req.product() == null) ? "" : req.product().trim().toLowerCase();
    String productDocs = docsService.getDocs(product);
    productDocs = docsService.trim(productDocs, 7000); // ajusta si quieres

    String instructions = buildInstructions(product, productDocs);

    String answer = openAiResponsesClient.generateAnswer(instructions, req.message());

    // Si OpenAI regresa error, mejor marcar ok=false
    if (answer != null && answer.startsWith("OpenAI error")) {
      return Map.of("ok", false, "error", answer, "product", product);
    }

    return Map.of(
        "ok", true,
        "product", product,
        "answer", answer
    );
  }

  private String buildInstructions(String product, String docs) {
    // Si no hay docs, no inventamos: pedimos base URL / info mínima.
    String docsBlock = (docs == null || docs.isBlank())
        ? "NO HAY DOCS CARGADAS PARA ESTE PRODUCTO. No inventes. Pide lo mínimo."
        : docs;

    return """
Eres Nexus, el chatbot oficial de evi_link.

Responde en español, claro y práctico.
Enfócate SOLO en el producto: %s.

REGLAS IMPORTANTES:
- Usa ÚNICAMENTE el CONTEXTO (docs) para endpoints, headers, límites, URLs y ejemplos.
- NO inventes dominios/hosts/URLs completas. Si la base URL no está en el contexto, responde solo con el path (ej: /api/curp/validate) y pide la base URL del ambiente.
- Si falta información en las docs, dilo y pregunta lo mínimo.
- Si el usuario pide “cómo integrar”, da pasos + ejemplo curl.

CONTEXTO (DOCS DEL PRODUCTO):
------------------------------
%s
------------------------------
""".formatted(product, docsBlock);
  }

  public record ChatRequest(
      @NotBlank String sessionId,
      @NotBlank String product,
      @NotBlank String message
  ) {}
}
