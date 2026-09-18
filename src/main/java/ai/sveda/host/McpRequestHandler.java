package ai.sveda.host;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpRequestHandler {
    static final String MCP_PROTOCOL_VERSION = "2025-11-25";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpRequestHandler() {
    }

    static McpHttpResponse handle(SvedaHost host, String body, HostAuth auth, Map<String, String> headers) {
        Map<String, Object> payload = parseObject(body);
        String method = stringValue(payload.get("method"));
        Object id = payload.get("id");
        boolean isNotification = id == null;
        Map<String, Object> params = mapValue(payload.get("params"));
        HostCallContext context = new HostCallContext(
            auth == null ? null : auth.user(),
            readPageContext(headers),
            readChatId(headers)
        );

        if ("notifications/initialized".equals(method)) {
            return McpHttpResponse.empty(202);
        }

        if ("initialize".equals(method)) {
            Map<String, Object> result = initializeResult(host);
            return jsonRpc(200, id, result, sessionHeaders());
        }

        if ("tools/list".equals(method)) {
            int perPage = perPage(params);
            List<Map<String, Object>> tools = listTools(host);
            int start = cursorStart(params);
            int end = Math.min(tools.size(), start + perPage);
            List<Map<String, Object>> slice = tools.subList(start, end);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tools", slice);
            if (end < tools.size()) {
                result.put("nextCursor", String.valueOf(end));
            }
            return jsonRpc(200, id, result, Map.of());
        }

        if ("tools/call".equals(method)) {
            try {
                Map<String, Object> result = callTool(host, params, context);
                return jsonRpc(200, id, result, Map.of());
            } catch (Exception exception) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("content", List.of(Map.of("type", "text", "text", exception.getMessage())));
                result.put("isError", true);
                return jsonRpc(200, id, result, Map.of());
            }
        }

        if (isNotification) {
            return McpHttpResponse.empty(202);
        }

        return jsonRpcError(200, id, -32601, "Method not found: " + method);
    }

    private static Map<String, Object> initializeResult(SvedaHost host) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
        result.put("serverInfo", Map.of(
            "name", host.config().serverName(),
            "version", host.config().serverVersion()
        ));
        String instructions = host.config().instructions().trim();
        if (!instructions.isEmpty()) {
            result.put("instructions", instructions);
        }
        return result;
    }

    private static List<Map<String, Object>> listTools(SvedaHost host) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (HostTool tool : host.resolveTools()) {
            Map<String, Object> schema = tool.inputSchema();
            if (schema == null || schema.isEmpty()) {
                schema = Map.of("type", "object", "properties", Map.of());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.name());
            entry.put("title", tool.name());
            entry.put("description", tool.description());
            entry.put("inputSchema", schema);
            entry.put("annotations", annotations(tool.mode()));
            entry.put("_meta", Map.of("domain", tool.domain(), "mode", tool.mode()));
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> annotations(String mode) {
        if (HostModes.READ.equals(mode)) {
            return Map.of("readOnlyHint", true);
        }
        if (HostModes.DELETE.equals(mode)) {
            return Map.of("readOnlyHint", false, "destructiveHint", true);
        }
        return Map.of("readOnlyHint", false, "destructiveHint", false);
    }

    private static Map<String, Object> callTool(SvedaHost host, Map<String, Object> params, HostCallContext context)
        throws Exception {
        String name = stringValue(params.get("name"));
        Map<String, Object> arguments = mapValue(params.get("arguments"));

        for (HostTool tool : host.resolveTools()) {
            if (!tool.name().equals(name)) {
                continue;
            }
            Object output = tool.handle(arguments, context);
            return encodeToolResult(output);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", "Unknown tool: " + name)));
        result.put("isError", true);
        return result;
    }

    private static Map<String, Object> encodeToolResult(Object output) throws JsonProcessingException {
        String text;
        if (output instanceof String string) {
            text = string;
        } else if (output instanceof byte[] bytes) {
            text = new String(bytes);
        } else {
            text = MAPPER.writeValueAsString(output);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        result.put("isError", false);
        return result;
    }

    private static int perPage(Map<String, Object> params) {
        Object raw = params.get("per_page");
        if (raw == null) {
            raw = params.get("perPage");
        }
        int value = raw instanceof Number number ? number.intValue() : 250;
        return Math.min(250, Math.max(1, value));
    }

    private static int cursorStart(Map<String, Object> params) {
        Object cursor = params.get("cursor");
        if (cursor == null || String.valueOf(cursor).isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(cursor)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static Map<String, String> sessionHeaders() {
        return Map.of("mcp-protocol-version", MCP_PROTOCOL_VERSION, "mcp-session-id", "sess-" + System.currentTimeMillis());
    }

    private static McpHttpResponse jsonRpc(int status, Object id, Map<String, Object> result, Map<String, String> headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("result", result);
        return McpHttpResponse.json(status, stringify(body), headers);
    }

    private static McpHttpResponse jsonRpcError(int status, Object id, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("error", Map.of("code", code, "message", message));
        return McpHttpResponse.json(status, stringify(body), Map.of());
    }

    private static String stringify(Map<String, Object> value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode MCP response.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> readPageContext(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String raw = firstHeader(headers, "x-sveda-page-context");
        if (raw == null || raw.length() > 65536) {
            return null;
        }
        try {
            return parseObject(raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String readChatId(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String raw = firstHeader(headers, "x-sveda-chat-id");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private static String firstHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
