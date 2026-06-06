package com.evilink.nexus_api.mcpone.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class McpOneTriggerService {

    private static final List<String> VALIDATION_KEYWORDS = List.of(
        "validate",
        "validation",
        "curp",
        "identity",
        "verify",
        "rfc",
        "tax validation",
        "tax id",
        "digito verificador",
        "check digit"
    );

    private static final List<String> SECURITY_KEYWORDS = List.of(
        "risk",
        "fraud",
        "security",
        "trust",
        "scoring",
        "transaction risk",
        "fraud risk",
        "secrets",
        "secret management",
        "api key",
        "api keys",
        "credentials",
        "vault",
        "key management",
        "cryptography",
        "encrypted"
    );

    private static final List<String> MARKET_KEYWORDS = List.of(
        "trend",
        "market",
        "signal",
        "signals",
        "btc",
        "crypto",
        "price",
        "market context",
        "market trend",
        "market regime",
        "momentum",
        "anomalies"
    );

    private static final List<String> CAPABILITY_KEYWORDS = List.of(
        "capability",
        "capabilities",
        "module",
        "modules",
        "which module",
        "what capability",
        "fits this",
        "best module",
        "best fit",
        "what product",
        "which product",
        "product handles",
        "supports"
    );

    private static final List<String> DATA_KEYWORDS = List.of(
        "data analysis",
        "data processing",
        "analyze data",
        "process data",
        "transformation",
        "pipeline",
        "cleanup",
        "clean file",
        "deduplicate",
        "csv",
        "json",
        "structured file",
        "dataset cleanup",
        "structured file cleanup"
    );

    private static final List<String> OBSERVABILITY_KEYWORDS = List.of(
        "status",
        "service status",
        "health",
        "uptime",
        "monitoring",
        "incident",
        "observability"
    );

    private static final List<String> ECOSYSTEM_DISCOVERY_KEYWORDS = List.of(
        "does evilink have",
        "what does",
        "ecosystem",
        "product",
        "products",
        "service",
        "services"
    );

    private static final List<String> EXCLUSION_KEYWORDS = List.of(
        "how are you",
        "tell me a story",
        "story",
        "joke",
        "motivation",
        "general advice",
        "life advice",
        "poem",
        "song",
        "recipe"
    );

    public boolean shouldUseMcpOne(String product, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }

        String normalizedInput = normalize(userInput);
        String normalizedProduct = normalizeProduct(product);

        if (matchesAny(normalizedInput, EXCLUSION_KEYWORDS)) {
            return false;
        }

        // Regla principal de junio:
        // si el selector/producto es evilink, casi todo debe pasar por MCP-One.
        if ("evilink".equals(normalizedProduct)) {
            return true;
        }

        return matchesValidation(normalizedInput)
            || matchesSecurity(normalizedInput)
            || matchesMarket(normalizedInput)
            || matchesCapabilityDiscovery(normalizedInput)
            || matchesData(normalizedInput)
            || matchesObservability(normalizedInput)
            || matchesEcosystemDiscovery(normalizedInput);
    }

    public String detectDomainHint(String product, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "unknown";
        }

        String normalizedInput = normalize(userInput);
        String normalizedProduct = normalizeProduct(product);

        if ("evilink".equals(normalizedProduct) && matchesEcosystemDiscovery(normalizedInput)) {
            return "ecosystem_discovery";
        }
        if (matchesValidation(normalizedInput)) {
            return "validation";
        }
        if (matchesSecurity(normalizedInput)) {
            return "security_trust";
        }
        if (matchesMarket(normalizedInput)) {
            return "market_intelligence";
        }
        if (matchesCapabilityDiscovery(normalizedInput)) {
            return "capability_discovery";
        }
        if (matchesData(normalizedInput)) {
            return "data_operations";
        }
        if (matchesObservability(normalizedInput)) {
            return "observability";
        }
        if (matchesEcosystemDiscovery(normalizedInput)) {
            return "ecosystem_discovery";
        }

        return "unknown";
    }

    private boolean matchesValidation(String normalized) {
        return matchesAny(normalized, VALIDATION_KEYWORDS);
    }

    private boolean matchesSecurity(String normalized) {
        return matchesAny(normalized, SECURITY_KEYWORDS);
    }

    private boolean matchesMarket(String normalized) {
        return matchesAny(normalized, MARKET_KEYWORDS);
    }

    private boolean matchesCapabilityDiscovery(String normalized) {
        return matchesAny(normalized, CAPABILITY_KEYWORDS);
    }

    private boolean matchesData(String normalized) {
        return matchesAny(normalized, DATA_KEYWORDS);
    }

    private boolean matchesObservability(String normalized) {
        return matchesAny(normalized, OBSERVABILITY_KEYWORDS);
    }

    private boolean matchesEcosystemDiscovery(String normalized) {
        return matchesAny(normalized, ECOSYSTEM_DISCOVERY_KEYWORDS);
    }

    private boolean matchesAny(String normalized, List<String> keywords) {
        return keywords.stream().anyMatch(normalized::contains);
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).trim();
    }

    private String normalizeProduct(String product) {
        String p = normalize(product == null ? "" : product);
        return switch (p) {
            case "evi_link" -> "evilink";
            default -> p;
        };
    }
}