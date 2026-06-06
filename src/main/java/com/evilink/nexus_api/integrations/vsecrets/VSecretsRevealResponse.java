package com.evilink.nexus_api.integrations.vsecrets;

public record VSecretsRevealResponse(
        String id,
        String project_id,
        String key,
        String value,
        Integer version
) {}