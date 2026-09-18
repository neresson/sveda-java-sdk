package ai.sveda.host;

import java.util.Map;

public record McpHttpRequest(
    String method,
    String bearerToken,
    String body,
    Map<String, String> headers
) {
}
