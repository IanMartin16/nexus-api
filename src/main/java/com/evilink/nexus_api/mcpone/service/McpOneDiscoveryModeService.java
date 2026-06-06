package com.evilink.nexus_api.mcpone.service;

import com.evilink.nexus_api.mcpone.dto.McpOneDiscoveryMode;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import com.evilink.nexus_api.mcpone.registry.service.ProductRegistryService;
import org.springframework.stereotype.Service;

@Service
public class McpOneDiscoveryModeService {

    private final McpOneNearFitService mcpOneNearFitService;
    private final ProductRegistryService productRegistryService;

    public McpOneDiscoveryModeService(
            McpOneNearFitService mcpOneNearFitService,
            ProductRegistryService productRegistryService
    ) {
        this.mcpOneNearFitService = mcpOneNearFitService;
        this.productRegistryService = productRegistryService;
    }

    public McpOneDiscoveryMode detect(
            String userInput,
            String product,
            String displayState,
            McpOneNormalizedResult mcp
    ) {
        if (mcp == null) {
            return McpOneDiscoveryMode.GENERIC;
        }

        String nearFitProduct = mcpOneNearFitService.detectNearFitProduct(userInput, product);
        String recommendedModule = mcp.getRecommended_module();

        boolean hasRecommendedModule =
                recommendedModule != null && !recommendedModule.isBlank();

        if (nearFitProduct != null && !productRegistryService.isInternalOnly(nearFitProduct)) {
            return McpOneDiscoveryMode.NEAR_FIT;
        }

        if ("resolved".equalsIgnoreCase(displayState)
                && Boolean.FALSE.equals(mcp.getHandoff())
                && hasRecommendedModule
                && productRegistryService.canBeShownAsExactFit(recommendedModule)
                && productRegistryService.hasPrimaryExactFitCapability(recommendedModule)) {
            return McpOneDiscoveryMode.EXACT_FIT;
        }

        if (("handoff".equalsIgnoreCase(displayState)
                || Boolean.TRUE.equals(mcp.getHandoff()))
                && hasRecommendedModule
                && productRegistryService.canBeShownAsSpecializedRoute(recommendedModule)) {
            return McpOneDiscoveryMode.SPECIALIZED_ROUTE;
        }

        if (hasRecommendedModule && !productRegistryService.isInternalOnly(recommendedModule)) {
            return McpOneDiscoveryMode.GENERIC;
        }

        return McpOneDiscoveryMode.GENERIC;
    }
}