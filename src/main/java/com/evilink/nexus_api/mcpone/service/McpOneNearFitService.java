package com.evilink.nexus_api.mcpone.service;

import org.springframework.stereotype.Service;

@Service
public class McpOneNearFitService {

    public String detectNearFitProduct(String userInput, String product) {
        if (userInput == null || product == null) {
            return null;
        }

        String normalizedProduct = product.trim().toLowerCase();
        if (!"evilink".equals(normalizedProduct) && !"evi_link".equals(normalizedProduct)) {
            return null;
        }

        String msg = userInput.toLowerCase();

        boolean identityBroaderPrompt =
            msg.contains("identity verification") ||
            msg.contains("identity validation") ||
            msg.contains("validate identity data") ||
            msg.contains("identity workflows") ||
            msg.contains("identity api");

        if (identityBroaderPrompt) {
            return "curpify";
        }

        return null;
    }
}