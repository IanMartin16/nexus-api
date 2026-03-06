package com.evilink.nexus_api.chat;

import java.util.List;
import java.util.Map;

public class McpDtos {

  public static class McpResponse {
    public String responseVersion; // "0.1"
    public String traceId;
    public Answer answer;
    public List<ToolCall> toolCalls;
    public List<ToolResult> toolResults;

    public static class Answer {
      public String format; // "rich"
      public String summary;
      public List<Section> sections;
    }

    public static class Section {
      public String id;
      public String type;   // "text" | "notice" | "kpi_grid" | "table" | "bullets"
      public String title;

      // text
      public String text;

      // notice
      public String kind; // "info"|"warning"|"error"
      public String message;
      public String details;

      // (v0.1 puedes ir agregando más luego)
      public List<Map<String, Object>> items;  // para kpi_grid/bullets si quieres rápido
      public List<Map<String, Object>> columns; // para table
      public List<Map<String, Object>> rows;    // para table
      public Integer columnsCount; // para kpi_grid
    }

    public static class ToolCall {
      public String id;
      public String tool;
      public String version;
      public Map<String, Object> input;
      public Map<String, Object> policy;
      public String fiat;
    }

    public static class ToolResult {
      public String id;
      public String fiat;
      public String tool;
      public String toolCallId;
      public Long latencyMs;
      public String source;
      public String provider;
      public String asOf;
      public boolean ok;
      public String valueOf;
      public Map<String, Object> meta;
      public Object data;
      public String error;
      public Map<String, Object> output;
    }
  }

  public record McpChatRequest(String sessionId, String product, String message) {}
}