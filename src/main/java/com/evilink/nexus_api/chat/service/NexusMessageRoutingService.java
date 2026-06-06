package com.evilink.nexus_api.chat.service;

import com.evilink.nexus_api.chat.dto.NexusMessageRoutingResult;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import com.evilink.nexus_api.mcpone.service.McpOneDisplayStateResolver;
import com.evilink.nexus_api.mcpone.service.McpOneOrchestrationService;
import com.evilink.nexus_api.mcpone.service.McpOneTriggerService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class NexusMessageRoutingService {

    private final McpOneTriggerService triggerService;
    private final McpOneOrchestrationService orchestrationService;
    private final McpOneDisplayStateResolver displayStateResolver;
    private static final Logger log = LoggerFactory.getLogger(NexusMessageRoutingService.class);

    public NexusMessageRoutingService(
            McpOneTriggerService triggerService,
            McpOneOrchestrationService orchestrationService,
            McpOneDisplayStateResolver displayStateResolver
    ) {
        this.triggerService = triggerService;
        this.orchestrationService = orchestrationService;
        this.displayStateResolver = displayStateResolver;
    }

    public NexusMessageRoutingResult routeMessage(
            String product,
            String userInput,
            String requestId,
            String channel,
            String tenant,
            String sessionId
    ) {
        NexusMessageRoutingResult result = new NexusMessageRoutingResult();

        String domainHint = triggerService.detectDomainHint(product, userInput);
        boolean useMcpOne = triggerService.shouldUseMcpOne(product, userInput);

        result.setDomainHint(domainHint);
        result.setTriggered(useMcpOne);

        log.info(
            "mcp_one_gate product={} userInput='{}' domainHint={} useMcpOne={}",
             product,
            userInput,
            domainHint,
            useMcpOne
        );

        if (!useMcpOne) {
            result.setPath("nexus-native");
            result.setDisplayState(null);
            result.setMcpOne(null);
            return result;
        }

        McpOneNormalizedResult orchestration = orchestrationService.orchestrate(
                userInput,
                requestId,
                channel,
                tenant,
                sessionId
        );

        log.info(
        "mcp_one_orchestrate_call product={} requestId={} channel={} tenant={} sessionId={}",
            product,
            requestId,
            channel,
            tenant,
            sessionId
        );

        result.setPath("mcp-one");
        result.setDisplayState(displayStateResolver.resolve(orchestration));
        result.setMcpOne(orchestration);

        return result;
    }
}