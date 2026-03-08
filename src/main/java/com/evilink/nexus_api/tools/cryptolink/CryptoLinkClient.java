package com.evilink.nexus_api.tools.cryptolink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class CryptoLinkClient {

  private final WebClient http;
  private final Duration timeout;
  private final String apiKey;

  public CryptoLinkClient(
      WebClient.Builder builder,
      @Value("${cryptolink.base-url:https://cryptolink.mx}") String baseUrl,
      @Value("${cryptolink.timeout-ms:6000}") long timeoutMs,
      @Value("${cryptolink.api-key:}") String apiKey
  ) {
    this.http = builder.baseUrl(baseUrl).build();
    this.timeout = Duration.ofMillis(timeoutMs);
    this.apiKey = apiKey;
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

  @SuppressWarnings("unchecked")
  public Map<String, Object> getPrices(List<String> symbols, String fiat) {
    String symbolsCsv = String.join(",", symbols);

    return http.get()
        .uri(uriBuilder -> uriBuilder
            .path("/v1/prices")
            .queryParam("symbols", symbolsCsv)
            .queryParam("fiat", fiat)
            .build())
        .header("x-api-key", apiKey)
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(timeout)
        .block();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getMovers(List<String> symbols, String fiat, int limit) {
    String symbolsCsv = String.join(",", symbols);

    return http.get()
        .uri(uriBuilder -> uriBuilder
            .path("/v1/movers")
            .queryParam("symbols", symbolsCsv)
            .queryParam("fiat", fiat)
            .queryParam("limit", limit)
            .build())
        .header("x-api-key", apiKey)
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(timeout)
        .block();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getTrends(List<String> symbols, String fiat) {
    String symbolsCsv = String.join(",", symbols);

    return http.get()
        .uri(uriBuilder -> uriBuilder
            .path("/v1/trends")
            .queryParam("symbols", symbolsCsv)
            .queryParam("fiat", fiat)
            .build())
        .header("x-api-key", apiKey)
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(timeout)
        .block();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getPriceSpark(List<String> symbols, String fiat) {
    String symbolsCsv = String.join(",", symbols);

    return http.get()
        .uri(uriBuilder -> uriBuilder
            .path("/v1/prices/spark")
            .queryParam("symbols", symbolsCsv)
            .queryParam("fiat", fiat)
            .build())
        .header("x-api-key", apiKey)
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(timeout)
        .block();
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getMomentum(List<String> symbols, String fiat) {
    String symbolsCsv = String.join(",", symbols);

    return http.get()
        .uri(uriBuilder -> uriBuilder
            .path("/v1/momentum")
            .queryParam("symbols", symbolsCsv)
            .queryParam("fiat", fiat)
            .build())
        .header("x-api-key", apiKey)
        .retrieve()
        .bodyToMono(Map.class)
        .timeout(timeout)
        .block();
  }
}