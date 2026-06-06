package com.evilink.nexus_api.mcpone.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpOneNormalizedResult {
    private boolean ok;
    private String request_id;
    private String status;
    private String mode;
    private String summary;
    private String insight;
    private String recommended_module;
    private McpOneNextStep next_step;
    private Boolean handoff;
    private List<String> restrictions;
    private Double confidence;
    private List<String> reason_codes;
    private McpOneNormalizedError error;
    private String product_name;
    private String capability_name;
    private String discovery_mode;
    private String user_facing_title;
    private String user_facing_summary;
    private String user_facing_context;
    private String next_step_hint;
    private String offer_help;

    public static class McpOneNormalizedError {
        private String code;
        private String message;

        public McpOneNormalizedError() {
        }

        public McpOneNormalizedError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getRequest_id() {
        return request_id;
    }

    public void setRequest_id(String request_id) {
        this.request_id = request_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getInsight() {
        return insight;
    }

    public void setInsight(String insight) {
        this.insight = insight;
    }

    public String getRecommended_module() {
        return recommended_module;
    }

    public void setRecommended_module(String recommended_module) {
        this.recommended_module = recommended_module;
    }

    public McpOneNextStep getNext_step() {
        return next_step;
    }

    public void setNext_step(McpOneNextStep next_step) {
        this.next_step = next_step;
    }

    public Boolean getHandoff() {
        return handoff;
    }

    public void setHandoff(Boolean handoff) {
        this.handoff = handoff;
    }

    public List<String> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(List<String> restrictions) {
        this.restrictions = restrictions;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getReason_codes() {
        return reason_codes;
    }

    public void setReason_codes(List<String> reason_codes) {
        this.reason_codes = reason_codes;
    }

    public McpOneNormalizedError getError() {
        return error;
    }

    public void setError(McpOneNormalizedError error) {
        this.error = error;
    }

     public String getProduct_name() {
        return product_name;
    }

    public void setProduct_name(String product_name) {
        this.product_name = product_name;
    }

    public String getCapability_name() {
        return capability_name;
    }

    public void setCapability_name(String capability_name) {
        this.capability_name = capability_name;
    }

    public String getDiscovery_mode() {
        return discovery_mode;
    }

    public void setDiscovery_mode(String discovery_mode) {
        this.discovery_mode = discovery_mode;
    }

    public String getUser_facing_title() {
        return user_facing_title;
    }

    public void setUser_facing_title(String user_facing_title) {
        this.user_facing_title = user_facing_title;
    }

    public String getUser_facing_summary() {
        return user_facing_summary;
    }

    public void setUser_facing_summary(String user_facing_summary) {
        this.user_facing_summary = user_facing_summary;
    }

    public String getUser_facing_context() {
        return user_facing_context;
    }

    public void setUser_facing_context(String user_facing_context) {
        this.user_facing_context = user_facing_context;
    }

    public String getNext_step_hint() {
        return next_step_hint;
    }

    public void setNext_step_hint(String next_step_hint) {
        this.next_step_hint = next_step_hint;
    }

    public String getOffer_help() {
        return offer_help;
    }

    public void setOffer_help(String offer_help) {
        this.offer_help = offer_help;
    }
}