package com.evilink.nexus_api.mcpone.dto;

public class McpOneErrorEnvelope {
    private McpOneErrorPayload error;

    public McpOneErrorEnvelope() {
    }

    public McpOneErrorPayload getError() {
        return error;
    }

    public void setError(McpOneErrorPayload error) {
        this.error = error;
    }
}