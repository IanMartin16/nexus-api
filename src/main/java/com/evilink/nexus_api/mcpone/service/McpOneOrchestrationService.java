package com.evilink.nexus_api.mcpone.service;

import com.evilink.nexus_api.mcpone.client.McpOneAdapterClient;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import org.springframework.stereotype.Service;

@Service
public class McpOneOrchestrationService {

    private final McpOneAdapterClient adapterClient;

    public McpOneOrchestrationService(McpOneAdapterClient adapterClient) {
        this.adapterClient = adapterClient;
    }

    public McpOneNormalizedResult orchestrate(
            String userInput,
            String requestId,
            String channel,
            String tenant,
            String sessionId
    ) {
        return adapterClient.orchestrate(
                userInput,
                requestId,
                channel,
                tenant,
                sessionId
        );
    }
}