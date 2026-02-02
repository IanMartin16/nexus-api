package com.evilink.nexus_api.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

  private final boolean enabled;
  private final int perMinute;

  // key = sessionId|product
  private final Map<String, Window> buckets = new ConcurrentHashMap<>();

  public RateLimiterService(
      @Value("${nexus.rate.enabled:true}") boolean enabled,
      @Value("${nexus.rate.perMinute:20}") int perMinute
  ) {
    this.enabled = enabled;
    this.perMinute = perMinute;
  }

  public boolean allow(String sessionId, String product) {
    if (!enabled) return true;

    String sid = (sessionId == null || sessionId.isBlank()) ? "web" : sessionId.trim();
    String p = (product == null || product.isBlank()) ? "curpify" : product.trim().toLowerCase();
    String key = sid + "|" + p;

    long now = System.currentTimeMillis();

    Window w = buckets.compute(key, (k, cur) -> {
      if (cur == null || now >= cur.resetAtMs) {
        return new Window(1, now + 60_000L);
      }
      cur.count++;
      return cur;
    });

    return w.count <= perMinute;
  }

  private static class Window {
    int count;
    long resetAtMs;
    Window(int count, long resetAtMs) { this.count = count; this.resetAtMs = resetAtMs; }
  }
}
