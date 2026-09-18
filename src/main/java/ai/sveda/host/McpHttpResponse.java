package ai.sveda.host;

import java.util.Map;

public record McpHttpResponse(
    int statusCode,
    String body,
    Map<String, String> headers
) {
    public static McpHttpResponse empty(int statusCode) {
        return new McpHttpResponse(statusCode, "", Map.of());
    }

    public static McpHttpResponse json(int statusCode, String json, Map<String, String> headers) {
        return new McpHttpResponse(statusCode, json, headers);
    }
}
