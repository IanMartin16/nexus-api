package com.evilink.nexus_api.mcpone.service;

import com.evilink.nexus_api.mcpone.dto.McpOneDiscoveryMode;
import com.evilink.nexus_api.mcpone.dto.McpOneNormalizedResult;
import com.evilink.nexus_api.mcpone.dto.McpOneWidgetCopy;
import com.evilink.nexus_api.mcpone.registry.dto.RegistryCapability;
import com.evilink.nexus_api.mcpone.registry.service.ProductRegistryService;
import org.springframework.stereotype.Service;

@Service
public class McpOneWidgetCopyService {

    private final ProductRegistryService productRegistryService;
    private final McpOneNearFitService mcpOneNearFitService;


    public McpOneWidgetCopyService(ProductRegistryService productRegistryService) {
        this.productRegistryService = productRegistryService;
        this.mcpOneNearFitService = new McpOneNearFitService();
    }

    public McpOneWidgetCopy build(
            String userInput,
            String product,
            String displayState,
            McpOneDiscoveryMode discoveryMode,
            McpOneNormalizedResult mcp
    ) {
        
        McpOneWidgetCopy copy = new McpOneWidgetCopy();

        String summary = mcp != null && mcp.getSummary() != null ? mcp.getSummary().trim() : "";
        String insight = mcp != null && mcp.getInsight() != null ? mcp.getInsight().trim() : "";
        String recommendedModule = mcp != null ? mcp.getRecommended_module() : null;

        if (discoveryMode == McpOneDiscoveryMode.EXACT_FIT) {
            copy.setBadge("Producto recomendado");
            copy.setTitle("Mejor coincidencia");

            String productTitle = mcp != null ? mcp.getUser_facing_title() : null;
            String productSummary = mcp != null ? mcp.getUser_facing_summary() : null;
            String productContext = mcp != null ? mcp.getUser_facing_context() : null;
            String capabilityName = mcp != null ? mcp.getCapability_name() : null;

            if (productTitle != null && !productTitle.isBlank()) {
                copy.setMessage(productTitle);

                if (productSummary != null && !productSummary.isBlank()) {
                    if (capabilityName != null && !capabilityName.isBlank()) {
                        copy.setSecondaryMessage(
                            productSummary + " Capacidad principal: " + capabilityName + "."
                        );
                    } else {
                        copy.setSecondaryMessage(productSummary);
                    }
                } else if (productContext != null && !productContext.isBlank()) {
                    copy.setSecondaryMessage(productContext);
                } else {
                    copy.setSecondaryMessage(
                "Es la mejor coincidencia actual dentro del ecosistema para este caso."
                    );
                }

                return copy;
            }

             // fallback temporal al registry local
            if (recommendedModule != null && !recommendedModule.isBlank()) {
                String moduleName = productRegistryService.resolveProductName(recommendedModule);
                String moduleSummary = productRegistryService.resolveProductSummary(recommendedModule);
                RegistryCapability primaryCapability =
                    productRegistryService.findPrimaryPublicCapability(recommendedModule).orElse(null);

                    copy.setMessage(moduleName);

                    if (moduleSummary != null && !moduleSummary.isBlank()) {
                        if (primaryCapability != null
                            && primaryCapability.getName() != null
                            && !primaryCapability.getName().isBlank()) {
                            copy.setSecondaryMessage(
                                 moduleSummary + " Capacidad principal: " + primaryCapability.getName() + "."
                            );
                        } else {
                            copy.setSecondaryMessage(moduleSummary);
                        }
                    } else {
                        copy.setSecondaryMessage(
                "Es la mejor coincidencia actual dentro del ecosistema para este caso."
                        );
                    }

                    return copy;
            }
    }

    if (discoveryMode == McpOneDiscoveryMode.NEAR_FIT) {
        copy.setBadge("Coincidencia cercana");
        copy.setTitle("Orientación útil");

        String nearFitProduct = mcpOneNearFitService.detectNearFitProduct(userInput, product);
        String effectiveProduct = nearFitProduct != null ? nearFitProduct : recommendedModule;

        if (effectiveProduct != null && !effectiveProduct.isBlank()) {
            String moduleName = productRegistryService.resolveProductName(effectiveProduct);
            String moduleSummary = productRegistryService.resolveProductSummary(effectiveProduct);

            copy.setMessage(
                "No existe una capacidad general de ese tipo dentro de Evilink, pero la coincidencia más cercana dentro del ecosistema es " + moduleName + "."
            );

            if (moduleSummary != null && !moduleSummary.isBlank()) {
                copy.setSecondaryMessage(moduleSummary);
            } else {
                copy.setSecondaryMessage(
                "Es la dirección más cercana actualmente disponible dentro del ecosistema para este caso."
                );
            }
        } else {
            copy.setMessage(
            "No existe exactamente en esa forma amplia, pero encontré una coincidencia cercana dentro del ecosistema."
            );
            copy.setSecondaryMessage(
                insight.isBlank() ? null : insight
            );
        }

        return copy;
    }

        if (discoveryMode == McpOneDiscoveryMode.SPECIALIZED_ROUTE) {
            copy.setBadge("Ruta especializada");
            copy.setTitle("Siguiente paso");

            if (recommendedModule != null && !recommendedModule.isBlank()) {
                String moduleName = productRegistryService.resolveProductName(recommendedModule);
                boolean limited = productRegistryService.isLimitedExposure(recommendedModule);
                boolean specialized = productRegistryService.isSpecializedRoute(recommendedModule);

                if (specialized || limited) {
                    copy.setMessage(
                        "La mejor dirección actual dentro del ecosistema para este caso es " + moduleName + ", pero requiere un flujo más específico antes de continuar."
                    );
                } else {
                    copy.setMessage(
                        "La mejor dirección actual dentro del ecosistema para este caso es " + moduleName + "."
                    );
                }
            } else {
                copy.setMessage(
                    "Encontré una ruta del ecosistema relacionada con esta necesidad, pero requiere un flujo más específico antes de continuar."
                );
            }

            copy.setSecondaryMessage(
        "Esta solicitud apunta a una capacidad especializada y no a una función general disponible directamente en el widget."
            );
            return copy;
        }

        switch (displayState == null ? "resolved" : displayState) {
            case "preview" -> {
                copy.setBadge("Capacidad limitada");
                copy.setTitle("Vista previa");
                copy.setMessage(
                    !summary.isBlank()
                        ? summary
                        : "Existe una capacidad relacionada, pero hoy solo está disponible de forma limitada."
                );
                copy.setSecondaryMessage(
                    !insight.isBlank()
                        ? insight
                        : "Puedo orientarte con lo que actualmente existe dentro del ecosistema."
                );
            }

            case "fallback" -> {
                copy.setBadge("Sin coincidencia clara");
                copy.setTitle("Alcance actual");
                copy.setMessage(
                    "Hoy Evilink no cubre esta necesidad de forma clara dentro de sus productos actuales."
                );
                copy.setSecondaryMessage(
                    "Actualmente el ecosistema se enfoca en APIs, servicios y rutas especializadas como Curpify, CryptoLink, Data_Link, V-Secrets, Status-hub y Secure Link."
                );
            }

            default -> {
                copy.setBadge("Ruta especializada");
                copy.setTitle("Respuesta");
                copy.setMessage(
                    !summary.isBlank()
                        ? summary
                        : "Encontré una respuesta adecuada dentro del ecosistema."
                );
                copy.setSecondaryMessage(
                    insight.isBlank() ? null : insight
                );
            }
        }

        return copy;
    }
}