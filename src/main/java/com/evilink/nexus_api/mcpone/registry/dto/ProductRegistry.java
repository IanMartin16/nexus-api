package com.evilink.nexus_api.mcpone.registry.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRegistry {
    private List<RegistryProduct> products;

    public List<RegistryProduct> getProducts() {
        return products;
    }

    public void setProducts(List<RegistryProduct> products) {
        this.products = products;
    }
}