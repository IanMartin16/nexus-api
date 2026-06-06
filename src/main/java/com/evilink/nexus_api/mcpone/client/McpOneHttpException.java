package com.evilink.nexus_api.mcpone.client;

import com.evilink.nexus_api.mcpone.dto.McpOneErrorEnvelope;

public class McpOneHttpException extends RuntimeException {
    private final int statusCode;
    private final McpOneErrorEnvelope envelope;

    public McpOneHttpException(int statusCode, McpOneErrorEnvelope envelope) {
        super("MCP-One HTTP error: " + statusCode);
        this.statusCode = statusCode;
        this.envelope = envelope;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public McpOneErrorEnvelope getEnvelope() {
        return envelope;
    }
}