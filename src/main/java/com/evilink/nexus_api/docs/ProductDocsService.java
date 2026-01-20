package com.evilink.nexus_api.docs;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProductDocsService {

  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public String getDocs(String product) {
    String p = (product == null) ? "" : product.trim().toLowerCase();

    return cache.computeIfAbsent(p, key -> {
      String path = switch (key) {
        case "curpify" -> "knowledge/curpify.md";
        case "cryptolink" -> "knowledge/cryptolink.md";
        case "evilink" -> "knowledge/evilink.md";
        default -> "";
      };

      if (path.isBlank()) return "";

      try {
        ClassPathResource r = new ClassPathResource(path);
        if (!r.exists()) return "";
        byte[] bytes = r.getInputStream().readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
      } catch (Exception e) {
        return "";
      }
    });
  }

  /** evita meter docs gigantes al prompt */
  public String trim(String s, int maxChars) {
    if (s == null) return "";
    String t = s.trim();
    if (t.length() <= maxChars) return t;
    return t.substring(0, maxChars) + "\n\n[...doc recortado por tamaño...]";
  }
}
