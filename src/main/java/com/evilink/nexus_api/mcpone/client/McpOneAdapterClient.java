package com.evilink.nexus_api.mcpone.client;

import com.evilink.nexus_api.mcpone.dto.McpOneErrorEnvelope;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import com.evilink.nexus_api.mcpone.dto.McpOneRequest;
import com.evilink.nexus_api.mcpone.dto.McpOneResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class McpOneAdapterClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public McpOneAdapterClient(RestClient mcpOneRestClient, ObjectMapper objectMapper) {
        this.restClient = mcpOneRestClient;
        this.objectMapper = objectMapper;
    }

    public McpOneNormalizedResult orchestrate(
            String userInput,
            String requestId,
            String channel,
            String tenant,
            String sessionId
    ) {
        Map<String, Object> clientContext = new HashMap<>();
        clientContext.put("source", "nexus");
        clientContext.put("channel", channel);
        if (tenant != null) clientContext.put("tenant", tenant);
        if (sessionId != null) clientContext.put("session_id", sessionId);

        McpOneRequest request = new McpOneRequest(userInput, requestId, clientContext);

        try {
            return restClient.post()
                    .uri("/orchestrate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        int statusCode = clientResponse.getStatusCode().value();

                        try (InputStream bodyStream = clientResponse.getBody()) {
                            if (statusCode >= 200 && statusCode < 300) {
                                McpOneResponse response =
                                        objectMapper.readValue(bodyStream, McpOneResponse.class);
                                return normalizeSuccess(response, requestId);
                            }

                            McpOneErrorEnvelope errorEnvelope =
                                    objectMapper.readValue(bodyStream, McpOneErrorEnvelope.class);

                            String errorRequestId = requestId;
                            String code = "INTERNAL_ERROR";
                            String message = "The request could not be completed.";

                            if (errorEnvelope != null && errorEnvelope.getError() != null) {
                                if (errorEnvelope.getError().getRequest_id() != null) {
                                    errorRequestId = errorEnvelope.getError().getRequest_id();
                                }
                                if (errorEnvelope.getError().getCode() != null) {
                                    code = errorEnvelope.getError().getCode();
                                }
                                if (errorEnvelope.getError().getMessage() != null) {
                                    message = errorEnvelope.getError().getMessage();
                                }
                            }

                            return buildFailure(errorRequestId, code, message);

                        } catch (IOException ex) {
                            return buildFailure(requestId, "INTERNAL_ERROR", "The service returned an invalid response.");
                        }
                    });

        } catch (ResourceAccessException ex) {
            String code = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out")
                    ? "TIMEOUT"
                    : "UPSTREAM_UNAVAILABLE";

            String message = "TIMEOUT".equals(code)
                    ? "The request timed out before completion."
                    : "The service is temporarily unavailable.";

            return buildFailure(requestId, code, message);

        } catch (Exception ex) {
            return buildFailure(requestId, "INTERNAL_ERROR", "The request could not be completed.");
        }
    }

    private McpOneNormalizedResult normalizeSuccess(McpOneResponse response, String fallbackRequestId) {
        McpOneNormalizedResult result = new McpOneNormalizedResult();
        result.setOk(true);
        result.setRequest_id(response.getRequest_id() != null ? response.getRequest_id() : fallbackRequestId);
        result.setStatus(response.getStatus());
        result.setMode(response.getMode());
        result.setSummary(response.getSummary());
        result.setInsight(response.getInsight());
        result.setRecommended_module(response.getRecommended_module());
        result.setNext_step(response.getNext_step());
        result.setHandoff(response.isHandoff());
        result.setRestrictions(response.getRestrictions() != null ? response.getRestrictions() : List.of());
        result.setConfidence(response.getConfidence());
        result.setReason_codes(response.getReason_codes() != null ? response.getReason_codes() : List.of());
        result.setProduct_name(response.getProduct_name());
        result.setCapability_name(response.getCapability_name());
        result.setDiscovery_mode(response.getDiscovery_mode());
        result.setUser_facing_title(response.getUser_facing_title());
        result.setUser_facing_summary(response.getUser_facing_summary());
        result.setUser_facing_context(response.getUser_facing_context());
        result.setNext_step_hint(response.getNext_step_hint());
        result.setOffer_help(response.getOffer_help());
        return result;
    }

    private McpOneNormalizedResult buildFailure(String requestId, String code, String message) {
        McpOneNormalizedResult result = new McpOneNormalizedResult();
        result.setOk(false);
        result.setRequest_id(requestId);
        result.setError(new McpOneNormalizedResult.McpOneNormalizedError(code, message));
        return result;
    }
}