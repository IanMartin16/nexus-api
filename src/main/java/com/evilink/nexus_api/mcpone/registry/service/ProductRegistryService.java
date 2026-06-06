package com.evilink.nexus_api.mcpone.registry.service;

import com.evilink.nexus_api.mcpone.registry.dto.ProductRegistry;
import com.evilink.nexus_api.mcpone.registry.dto.RegistryCapability;
import com.evilink.nexus_api.mcpone.registry.dto.RegistryProduct;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
public class ProductRegistryService {

    private ProductRegistry registry;

    @PostConstruct
    public void loadRegistry() {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            ClassPathResource resource = new ClassPathResource("registry/products.yml");

            try (InputStream is = resource.getInputStream()) {
                this.registry = mapper.readValue(is, ProductRegistry.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to load product registry", e);
        }
    }

    public List<RegistryProduct> getAllProducts() {
        return registry != null && registry.getProducts() != null
            ? registry.getProducts()
            : List.of();
    }

    public List<RegistryProduct> getPublicProducts() {
        return getAllProducts().stream()
            .filter(p -> "public".equalsIgnoreCase(p.getExposure()))
            .toList();
    }

    public Optional<RegistryProduct> findProductById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return getAllProducts().stream()
            .filter(p -> id.equalsIgnoreCase(p.getId()))
            .findFirst();
    }

    public String resolveProductName(String productId) {
        return findProductById(productId)
            .map(RegistryProduct::getName)
            .orElse(productId);
    }

    public String resolveProductSummary(String productId) {
        return findProductById(productId)
           .map(RegistryProduct::getSummary)
           .orElse("");
    }

    public boolean isLimitedExposure(String productId) {
        return findProductById(productId)
            .map(p -> "limited".equalsIgnoreCase(p.getExposure()))
            .orElse(false);
    }

    public boolean isSpecializedRoute(String productId) {
        return findProductById(productId)
            .map(p -> "specialized_route".equalsIgnoreCase(p.getPositioning()))
            .orElse(false);
    }

    public Optional<RegistryCapability> findPrimaryPublicCapability(String productId) {
    return findProductById(productId)
        .flatMap(product -> {
            if (product.getCapabilities() == null || product.getCapabilities().isEmpty()) {
                return Optional.empty();
            }

            return product.getCapabilities().stream()
                .filter(capability -> "public".equalsIgnoreCase(capability.getExposure()))
                .findFirst();
        });
    }

    public String resolveExposure(String productId) {
        return findProductById(productId)
           .map(RegistryProduct::getExposure)
           .orElse("");
    }

    public boolean isInternalOnly(String productId) {
        return findProductById(productId)
            .map(product -> "internal_only".equalsIgnoreCase(product.getExposure()))
            .orElse(false);
    }

    public boolean canBeShownAsExactFit(String productId) {
        return findProductById(productId)
            .map(product -> "public".equalsIgnoreCase(product.getExposure()))
            .orElse(false);
    }

    public boolean canBeShownAsSpecializedRoute(String productId) {
        return findProductById(productId)
            .map(product ->
                "limited".equalsIgnoreCase(product.getExposure())
                || "public".equalsIgnoreCase(product.getExposure())
            )
            .orElse(false);
    }

    public boolean hasPrimaryExactFitCapability(String productId) {
        return findPrimaryPublicCapability(productId)
            .map(capability ->
               "active".equalsIgnoreCase(capability.getStatus())
                && "public".equalsIgnoreCase(capability.getExposure())
                && "direct".equalsIgnoreCase(capability.getKind())
            )
            .orElse(false);
    }
    public boolean canCapabilitySupportExactFit(String productId) {
        return hasPrimaryExactFitCapability(productId);
    }
}