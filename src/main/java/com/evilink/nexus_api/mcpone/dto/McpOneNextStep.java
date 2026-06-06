package com.evilink.nexus_api.mcpone.dto;

public class McpOneNextStep {
    private String type;
    private String module;
    private String reason;

    public McpOneNextStep() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}