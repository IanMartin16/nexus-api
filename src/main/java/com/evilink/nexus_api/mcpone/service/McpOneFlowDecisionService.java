package com.evilink.nexus_api.mcpone.service;

import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import org.springframework.stereotype.Service;


@Service
public class McpOneFlowDecisionService {

    private final McpOneTriggerService triggerService;
    private final McpOneOrchestrationService orchestrationService;

    public McpOneFlowDecisionService(
            McpOneTriggerService triggerService,
            McpOneOrchestrationService orchestrationService
    ) {
        this.triggerService = triggerService;
        this.orchestrationService = orchestrationService;
    }

    public boolean shouldRouteToMcpOne(String userInput) {
        return triggerService.shouldUseMcpOne("evilink", userInput);
    }

    public McpOneNormalizedResult orchestrateIfNeeded(
            String userInput,
            String requestId,
            String channel,
            String tenant,
            String sessionId
    ) {
        return orchestrationService.orchestrate(
                userInput,
                requestId,
                channel,
                tenant,
                sessionId
        );
    }
}