package ai.sveda.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sveda.client.HostSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HostTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void startSessionSendsMcpFields() throws Exception {
        var captured = new LinkedHashMap<String, Object>();
        try (MockSidecar sidecar = MockSidecar.start(captured)) {
        SvedaHost host = SvedaHost.create(new HostConfig()
            .baseUrl(sidecar.baseUrl)
            .hostApiKey("host-secret")
            .mcpUrl("https://app.test/mcp/sveda"));
        host.resolveToolsUsing(() -> List.of(new EchoTool()));

        HostSession session = host.startSession(Map.of("id", "go-playground"));
        assertEquals("embed-token", session.token());
        assertEquals("https://app.test/mcp/sveda", captured.get("host_mcp_url"));
        assertNotNull(captured.get("host_mcp_token"));
        assertFalse(String.valueOf(captured.get("host_mcp_token")).isBlank());
        }
    }

    @Test
    void mcpRequiresAuthentication() {
        SvedaHost host = SvedaHost.create(new HostConfig());
        host.resolveToolsUsing(() -> List.of(new EchoTool()));

        McpHttpResponse response = host.serve(new McpHttpRequest(
            "POST",
            "",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}",
            Map.of()
        ));

        assertEquals(401, response.statusCode());
    }

    @Test
    void mcpListsAndCallsTools() throws Exception {
        SvedaHost host = SvedaHost.create(new HostConfig()
            .serverName("Playground Feed")
            .instructions("Feed tools for the current user."));
        host.resolveToolsUsing(() -> List.of(new EchoTool()));
        String token = host.tokenStore().mint("user-1");

        McpHttpResponse init = host.serve(mcpRequest(token, "initialize", Map.of(
            "protocolVersion", "2025-11-25",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "test", "version", "0.1.0")
        ), 1));
        assertEquals(200, init.statusCode());
        Map<String, Object> initBody = parse(init.body());
        Map<String, Object> result = map(initBody.get("result"));
        Map<String, Object> serverInfo = map(result.get("serverInfo"));
        assertEquals("Playground Feed", serverInfo.get("name"));
        assertEquals("Feed tools for the current user.", result.get("instructions"));

        McpHttpResponse list = host.serve(mcpRequest(token, "tools/list", Map.of("per_page", 250), 2));
        Map<String, Object> listBody = parse(list.body());
        List<Map<String, Object>> tools = list(map(listBody.get("result")).get("tools"));
        assertEquals(1, tools.size());
        assertEquals("echo_message", tools.get(0).get("name"));
        Map<String, Object> meta = map(tools.get(0).get("_meta"));
        assertEquals("demo", meta.get("domain"));
        assertEquals("read", meta.get("mode"));
        assertFalse(meta.containsKey("confirmation"));

        McpHttpResponse call = host.serve(mcpRequest(token, "tools/call", Map.of(
            "name", "echo_message",
            "arguments", Map.of("message", "hello")
        ), 3));
        Map<String, Object> callBody = parse(call.body());
        Map<String, Object> callResult = map(callBody.get("result"));
        assertEquals(false, callResult.get("isError"));
        List<Map<String, Object>> content = list(callResult.get("content"));
        Map<String, Object> decoded = parse(String.valueOf(content.get(0).get("text")));
        Map<String, Object> data = map(decoded.get("data"));
        assertEquals("hello", data.get("message"));
    }

    @Test
    void confirmationMetaIsPublishedWhenRequired() throws Exception {
        SvedaHost host = SvedaHost.create(new HostConfig());
        host.resolveToolsUsing(() -> List.of(new EchoTool(), new DeleteTool()));
        String token = host.tokenStore().mint("user-1");

        McpHttpResponse list = host.serve(mcpRequest(token, "tools/list", Map.of(), 1));
        List<Map<String, Object>> tools = list(map(parse(list.body()).get("result")).get("tools"));
        Map<String, Object> echoMeta = Map.of();
        Map<String, Object> deleteMeta = Map.of();
        for (Map<String, Object> tool : tools) {
            if ("echo_message".equals(tool.get("name"))) {
                echoMeta = map(tool.get("_meta"));
            }
            if ("delete_post".equals(tool.get("name"))) {
                deleteMeta = map(tool.get("_meta"));
            }
        }
        assertFalse(echoMeta.containsKey("confirmation"));
        assertEquals("required", deleteMeta.get("confirmation"));
        assertEquals("delete", deleteMeta.get("mode"));
    }

    @Test
    void startSessionSendsPolicy() throws Exception {
        var captured = new LinkedHashMap<String, Object>();
        try (MockSidecar sidecar = MockSidecar.start(captured)) {
            SvedaHost host = SvedaHost.create(new HostConfig()
                .baseUrl(sidecar.baseUrl)
                .hostApiKey("host-secret")
                .mcpUrl("https://app.test/mcp/sveda"));
            host.resolveToolsUsing(() -> List.of(new EchoTool()));
            host.policyUsing(user -> "agent");

            HostSession session = host.startSession(Map.of("id", "user-1"));
            assertEquals("embed-token", session.token());
            assertEquals("agent", captured.get("policy"));
        }
    }

    @Test
    void toolsCallFiltersByAuthenticatedUser() throws Exception {
        SvedaHost host = SvedaHost.create(new HostConfig());
        host.resolveToolsUsing(user -> {
            if (user instanceof Map<?, ?> map && "user-1".equals(String.valueOf(map.get("id")))) {
                return List.of(new EchoTool());
            }
            return List.of();
        });

        String allowed = host.tokenStore().mint("user-1");
        String denied = host.tokenStore().mint("other");

        McpHttpResponse ok = host.serve(mcpRequest(allowed, "tools/call", Map.of(
            "name", "echo_message",
            "arguments", Map.of("message", "hello")
        ), 1));
        Map<String, Object> okBody = parse(ok.body());
        assertEquals(false, map(okBody.get("result")).get("isError"));

        McpHttpResponse blocked = host.serve(mcpRequest(denied, "tools/call", Map.of(
            "name", "echo_message",
            "arguments", Map.of("message", "hello")
        ), 2));
        Map<String, Object> blockedBody = parse(blocked.body());
        Map<String, Object> blockedResult = map(blockedBody.get("result"));
        assertEquals(true, blockedResult.get("isError"));
        List<Map<String, Object>> content = list(blockedResult.get("content"));
        assertTrue(String.valueOf(content.get(0).get("text")).contains("Unknown tool"));
    }

    @Test
    void zeroArgResolveToolsCallbackStillWorks() throws Exception {
        SvedaHost host = SvedaHost.create(new HostConfig());
        host.resolveToolsUsing(() -> List.of(new EchoTool()));
        String token = host.tokenStore().mint("user-1");

        McpHttpResponse list = host.serve(mcpRequest(token, "tools/list", Map.of("per_page", 250), 1));
        List<Map<String, Object>> tools = list(map(parse(list.body()).get("result")).get("tools"));
        assertEquals(1, tools.size());
        assertEquals("echo_message", tools.get(0).get("name"));
    }

    @Test
    void describeMatchesMcpToolsList() throws Exception {
        SvedaHost host = SvedaHost.create(new HostConfig());
        host.resolveToolsUsing(user -> List.of(new EchoTool()));
        Object user = Map.of("id", "user-1");

        Map<String, Object> manifest = host.describe(user);
        assertEquals(HostManifest.SCHEMA, manifest.get("schema"));

        String token = host.tokenStore().mint("user-1");
        McpHttpResponse list = host.serve(mcpRequest(token, "tools/list", Map.of("per_page", 250), 1));
        List<Map<String, Object>> listed = list(map(parse(list.body()).get("result")).get("tools"));
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> tool : listed) {
            byName.put(String.valueOf(tool.get("name")), tool);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> manifestTools = (List<Map<String, Object>>) manifest.get("tools");
        for (Map<String, Object> tool : manifestTools) {
            Map<String, Object> listedTool = byName.get(String.valueOf(tool.get("name")));
            assertNotNull(listedTool);
            assertEquals(listedTool.get("description"), tool.get("description"));
            assertEquals(listedTool.get("_meta"), tool.get("_meta"));
        }
    }

    private static McpHttpRequest mcpRequest(String token, String method, Map<String, Object> params, int id)
        throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", id);
        payload.put("method", method);
        payload.put("params", params);
        return new McpHttpRequest(
            "POST",
            token,
            MAPPER.writeValueAsString(payload),
            Map.of("Authorization", "Bearer " + token)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) throws Exception {
        return MAPPER.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static class EchoTool implements HostTool {
        @Override
        public String name() {
            return "echo_message";
        }

        @Override
        public String description() {
            return "Echo a message back.";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return HostToolSchema.object(Map.of(
                "message", Map.of("type", "string", "description", "Message to echo", "required", true)
            ));
        }

        @Override
        public String mode() {
            return HostModes.READ;
        }

        @Override
        public String domain() {
            return "demo";
        }

        @Override
        public Object handle(Map<String, Object> arguments, HostCallContext context) {
            return Map.of(
                "success", true,
                "data", Map.of("message", arguments.get("message"))
            );
        }
    }

    private static final class DeleteTool extends EchoTool {
        @Override
        public String name() {
            return "delete_post";
        }

        @Override
        public String mode() {
            return HostModes.DELETE;
        }

        @Override
        public String confirmation() {
            return "required";
        }
    }
}
