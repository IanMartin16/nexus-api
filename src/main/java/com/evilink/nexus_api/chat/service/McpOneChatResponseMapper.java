package com.evilink.nexus_api.chat.service;

import com.evilink.nexus_api.chat.dto.NexusMessageRoutingResult;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class McpOneChatResponseMapper {

    public Map<String, Object> map(NexusMessageRoutingResult routed, String conversationId) {
        Map<String, Object> response = new HashMap<>();

        response.put("conversationId", conversationId);
        response.put("source", "mcp-one");
        response.put("path", routed.getPath());
        response.put("triggered", routed.isTriggered());
        response.put("displayState", routed.getDisplayState());
        response.put("domainHint", routed.getDomainHint());

        McpOneNormalizedResult mcp = routed.getMcpOne();
        if (mcp != null) {
            response.put("title", "MCP-One activo.");
            response.put("message", mcp.getSummary());
            response.put("insight", mcp.getInsight());
            response.put("recommendedModule", mcp.getRecommended_module());
            response.put("handoff", mcp.getHandoff());
            response.put("status", mcp.getStatus());
            response.put("mode", mcp.getMode());
            response.put("requestId", mcp.getRequest_id());
        }

        return response;
    }
}