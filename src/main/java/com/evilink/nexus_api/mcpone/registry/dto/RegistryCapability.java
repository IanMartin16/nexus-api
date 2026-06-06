package com.evilink.nexus_api.mcpone.registry.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistryCapability {
    private String id;
    private String name;
    private String status;
    private String exposure;
    private String kind;
    private String description;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExposure() { return exposure; }
    public void setExposure(String exposure) { this.exposure = exposure; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}