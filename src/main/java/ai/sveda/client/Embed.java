package ai.sveda.client;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Embed {
    private final HttpTransport transport;

    Embed(HttpTransport transport) {
        this.transport = transport;
    }

    public EmbedToken createToken(String visitorId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (visitorId != null && !visitorId.isBlank()) {
            params.put("visitor_id", visitorId);
        }
        return createToken(params);
    }

    public EmbedToken createToken(String visitorId, String hostMcpUrl, String hostMcpToken) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (visitorId != null && !visitorId.isBlank()) {
            params.put("visitor_id", visitorId);
        }
        if (hostMcpUrl != null) {
            params.put("host_mcp_url", hostMcpUrl);
        }
        if (hostMcpToken != null) {
            params.put("host_mcp_token", hostMcpToken);
        }
        return createToken(params);
    }

    public EmbedToken createToken(Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Object visitorId = params == null ? null : params.get("visitor_id");
        if (visitorId instanceof String visitor && !visitor.isBlank()) {
            payload.put("visitor_id", visitor);
        }
        Object url = params == null ? null : params.get("host_mcp_url");
        Object mcpToken = params == null ? null : params.get("host_mcp_token");
        if (url instanceof String hostMcpUrl && mcpToken instanceof String hostMcpToken
            && !hostMcpUrl.isBlank() && !hostMcpToken.isBlank()) {
            payload.put("host_mcp_url", hostMcpUrl);
            payload.put("host_mcp_token", hostMcpToken);
        }
        return EmbedToken.from(transport.requestJson("POST", "/sveda/embed/token", payload));
    }

    public Map<String, Object> config() {
        return transport.requestJson("GET", "/sveda/embed/config", Map.of());
    }
}
