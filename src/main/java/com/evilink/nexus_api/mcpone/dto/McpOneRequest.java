package com.evilink.nexus_api.mcpone.dto;

import java.util.Map;

public class McpOneRequest {
    private String user_input;
    private String request_id;
    private Map<String, Object> client_context;

    public McpOneRequest() {
    }

    public McpOneRequest(String userInput, String requestId, Map<String, Object> clientContext) {
        this.user_input = userInput;
        this.request_id = requestId;
        this.client_context = clientContext;
    }

    public String getUser_input() {
        return user_input;
    }

    public void setUser_input(String user_input) {
        this.user_input = user_input;
    }

    public String getRequest_id() {
        return request_id;
    }

    public void setRequest_id(String request_id) {
        this.request_id = request_id;
    }

    public Map<String, Object> getClient_context() {
        return client_context;
    }

    public void setClient_context(Map<String, Object> client_context) {
        this.client_context = client_context;
    }
}