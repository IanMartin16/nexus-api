package com.evilink.nexus_api.mcpone.service;

import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import org.springframework.stereotype.Component;

@Component
public class McpOneDisplayStateResolver {

    public String resolve(McpOneNormalizedResult result) {
        if (!result.isOk()) {
            return "service_error";
        }

        if ("handoff_required".equals(result.getStatus())) {
            return "handoff";
        }

        if ("preview".equals(result.getStatus())) {
            return "preview";
        }

        if ("fallback".equals(result.getStatus())) {
            return "fallback";
        }

        return "resolved";
    }
}