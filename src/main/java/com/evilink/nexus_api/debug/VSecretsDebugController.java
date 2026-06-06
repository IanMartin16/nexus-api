package com.evilink.nexus_api.debug;

import com.evilink.nexus_api.integrations.vsecrets.VSecretsClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VSecretsDebugController {

    private final VSecretsClient vSecretsClient;

    public VSecretsDebugController(VSecretsClient vSecretsClient) {
        this.vSecretsClient = vSecretsClient;
    }

    @GetMapping("/debug/vsecrets/openai")
    public Map<String, Object> testOpenAiSecret() {
        String secret = vSecretsClient.reveal("OPENAI_API_KEY");

        return Map.of(
                "secret_name", "OPENAI_API_KEY",
                "resolved", true,
                "preview", mask(secret)
        );
    }

    private String mask(String value) {
        if (value == null || value.length() < 10) {
            return "****";
        }

        return value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }
}