package com.evilink.nexus_api.integrations.vsecrets;

import com.evilink.nexus_api.config.VSecretsProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;

@Component
public class VSecretsClient {

    private final WebClient webClient;
    private final VSecretsProperties properties;

    public VSecretsClient(WebClient.Builder builder, VSecretsProperties properties) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", properties.getApiKey())
                .build();
    }

    public String reveal(String secretName) {
        VSecretsRevealResponse response = webClient.post()
            .uri(
                    "/api/v1/projects/{projectId}/secrets/{secretName}/reveal",
                    Map.of(
                            "projectId", properties.getProjectId(),
                            "secretName", secretName
                    )
            )
            .retrieve()
            .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                            .map(body -> new IllegalStateException(
                                    "V-Secrets request failed. Status: "
                                            + clientResponse.statusCode()
                                            + " Body: "
                                            + body
                            ))
            )
            .bodyToMono(VSecretsRevealResponse.class)
            .block();

        if (response == null || response.value() == null || response.value().isBlank()) {
            throw new IllegalStateException("V-Secrets returned an empty secret value");
        }

        return response.value();
    }
}