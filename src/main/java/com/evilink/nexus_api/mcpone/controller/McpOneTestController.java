package com.evilink.nexus_api.mcpone.controller;

import com.evilink.nexus_api.mcpone.dto.McpOneTestRequest;
import com.evilink.nexus_api.mcpone.service.McpOneOrchestrationService;
import com.evilink.nexus_api.mcpone.service.McpOneTriggerService;
import org.springframework.web.bind.annotation.*;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/mcp-one")
public class McpOneTestController {

    private final McpOneOrchestrationService orchestrationService;
    private final McpOneTriggerService triggerService;

    public McpOneTestController(
            McpOneOrchestrationService orchestrationService,
            McpOneTriggerService triggerService
    ) {
        this.orchestrationService = orchestrationService;
        this.triggerService = triggerService;
    }

    @PostMapping("/orchestrate")
    public Map<String, Object> orchestrate(@RequestBody McpOneTestRequest payload) {
        String userInput = payload.getUser_input() != null ? payload.getUser_input() : "";
        String requestId = payload.getRequest_id() != null ? payload.getRequest_id() : "nexus-manual-test";
        String channel = payload.getChannel() != null ? payload.getChannel() : "widget";
        String tenant = payload.getTenant() != null ? payload.getTenant() : "evilink-dev";
        String sessionId = payload.getSession_id() != null ? payload.getSession_id() : "sess-manual-test";

        boolean shouldUseMcpOne = triggerService.shouldUseMcpOne("evilink", userInput);

        Map<String, Object> response = new HashMap<>();
        response.put("triggered", shouldUseMcpOne);

        if (!shouldUseMcpOne) {
            response.put("path", "nexus-native");
            response.put("reason", "Prompt did not match MCP-One initial trigger rules.");
            return response;
        }

        response.put("path", "mcp-one");

        McpOneNormalizedResult orchestration = orchestrationService.orchestrate(
                userInput,
                requestId,
                channel,
                tenant,
                sessionId
        );

        response.put("result", orchestration);
        return response;
    }
}