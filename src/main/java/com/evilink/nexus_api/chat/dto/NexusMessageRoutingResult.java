package com.evilink.nexus_api.chat.dto;

import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;

public class NexusMessageRoutingResult {

    private boolean triggered;
    private String path;
    private String displayState;
    private String domainHint;
    private McpOneNormalizedResult mcpOne;

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered(boolean triggered) {
        this.triggered = triggered;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDisplayState() {
        return displayState;
    }

    public void setDisplayState(String displayState) {
        this.displayState = displayState;
    }

    public String getDomainHint() {
        return domainHint;
    }

    public void setDomainHint(String domainHint) {
        this.domainHint = domainHint;
    }

    public McpOneNormalizedResult getMcpOne() {
        return mcpOne;
    }

    public void setMcpOne(McpOneNormalizedResult mcpOne) {
        this.mcpOne = mcpOne;
    }
}