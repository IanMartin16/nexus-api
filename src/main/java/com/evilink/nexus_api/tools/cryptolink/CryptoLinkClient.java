package com.evilink.nexus_api.tools.cryptolink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Component
public class CryptoLinkClient {

  private final WebClient http;
  private final Duration timeout;

  public CryptoLinkClient(
      WebClient.Builder builder,
      @Value("${cryptolink.base-url:https://cryptolink.mx}") String baseUrl,
      @Value("${cryptolink.timeout-ms:6000}") long timeoutMs
  ) {
    this.http = builder.baseUrl(baseUrl).build();
    this.timeout = Duration.ofMillis(timeoutMs);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getSnapshot() {
    return http.get()
        .uri("/v1/snapshot")
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(timeout)
        .block();
  }
}