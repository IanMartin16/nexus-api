package com.evilink.nexus_api.mcpone.registry.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistryProduct {
    private String id;
    private String name;
    private String status;
    private String exposure;
    private String public_surface;
    private String frontend_status;
    private String positioning;
    private String summary;
    private List<String> domains;
    private List<RegistryCapability> capabilities;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getExposure() { return exposure; }
    public void setExposure(String exposure) { this.exposure = exposure; }

    public String getPublic_surface() { return public_surface; }
    public void setPublic_surface(String public_surface) { this.public_surface = public_surface; }

    public String getFrontend_status() { return frontend_status; }
    public void setFrontend_status(String frontend_status) { this.frontend_status = frontend_status; }

    public String getPositioning() { return positioning; }
    public void setPositioning(String positioning) { this.positioning = positioning; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getDomains() { return domains; }
    public void setDomains(List<String> domains) { this.domains = domains; }

    public List<RegistryCapability> getCapabilities() { return capabilities; }
    public void setCapabilities(List<RegistryCapability> capabilities) { this.capabilities = capabilities; }
}