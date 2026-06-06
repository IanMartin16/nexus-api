package com.evilink.nexus_api.mcpone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class McpOneClientConfig {

    @Bean
    public RestClient mcpOneRestClient(RestClient.Builder builder, McpOneProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());

        return builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}