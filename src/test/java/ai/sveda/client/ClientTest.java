package ai.sveda.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.sveda.client.exceptions.AuthenticationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientTest {
    @Test
    void issuesEmbedTokensWithHostCredentials() throws Exception {
        try (MockSidecar sidecar = new MockSidecar()) {
            sidecar.respondJson(200, """
                {"token":"sveda_embed_test","visitor_id":"spring-playground","expires_in":3600,"appearance":{"accent":"#c45c26"}}
                """);

            SvedaClient client = SvedaClient.builder()
                .baseUrl(sidecar.baseUrl())
                .hostApiKey("host-secret")
                .build();

            EmbedToken token = client.embed().createToken("spring-playground", "https://app.test/mcp/sveda", "mcp-token");

            assertEquals("sveda_embed_test", token.token());
            assertEquals("spring-playground", token.visitorId());
            assertEquals(3600, token.expiresIn());
            assertEquals("#c45c26", token.appearance().get("accent"));

            MockSidecar.RecordedRequest request = sidecar.lastRequest();
            assertEquals("POST", request.method());
            assertEquals("/sveda/embed/token", request.path());
            assertEquals("Bearer host-secret", request.authorization());
            assertNull(request.embedToken());
            assertTrue(request.body().contains("\"visitor_id\":\"spring-playground\""));
            assertTrue(request.body().contains("\"host_mcp_url\":\"https://app.test/mcp/sveda\""));
            assertTrue(request.body().contains("\"host_mcp_token\":\"mcp-token\""));

            HostSession session = SvedaClient.startHostSession(
                sidecar.baseUrl() + "/",
                token.token(),
                token.expiresIn(),
                token.appearance()
            );
            assertEquals(sidecar.baseUrl(), session.origin());
            assertEquals("sveda_embed_test", session.token());
            assertEquals(3600, session.expiresIn());
            assertEquals("#c45c26", session.appearance().get("accent"));
        }
    }

    @Test
    void startHostSessionMintsTokenForVisitor() throws Exception {
        try (MockSidecar sidecar = new MockSidecar()) {
            sidecar.respondJson(200, """
                {"token":"sveda_embed_host","visitor_id":"spring-playground","expires_in":1200}
                """);

            SvedaClient client = SvedaClient.builder()
                .baseUrl(sidecar.baseUrl())
                .hostApiKey("host-secret")
                .build();

            HostSession session = SvedaClient.startHostSession(client, "spring-playground");

            assertEquals(sidecar.baseUrl(), session.origin());
            assertEquals("sveda_embed_host", session.token());
            assertEquals(1200, session.expiresIn());
            assertEquals("POST", sidecar.lastRequest().method());
            assertEquals("/sveda/embed/token", sidecar.lastRequest().path());
            assertTrue(sidecar.lastRequest().body().contains("\"visitor_id\":\"spring-playground\""));
        }
    }

    @Test
    void streamsChatEventsWithEmbedToken() throws Exception {
        try (MockSidecar sidecar = new MockSidecar()) {
            sidecar.respondStream("data: {\"type\":\"message.start\"}\n\ndata: {\"type\":\"text.delta\",\"delta\":\"Hi\"}\n\ndata: [DONE]\n\n");

            SvedaClient client = SvedaClient.builder()
                .baseUrl(sidecar.baseUrl())
                .embedToken("embed-token")
                .build();

            List<StreamEvent> events = client.chat().createStreamed(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "Hello")),
                "chatId", "chat-1"
            ));

            assertEquals(2, events.size());
            assertEquals("message.start", events.get(0).type());
            assertEquals("text.delta", events.get(1).type());
            assertEquals("Hi", events.get(1).get("delta"));

            MockSidecar.RecordedRequest request = sidecar.lastRequest();
            assertEquals("POST", request.method());
            assertEquals("/sveda/stream", request.path());
            assertEquals("embed-token", request.embedToken());
            assertTrue(request.body().contains("\"chatId\":\"chat-1\""));
        }
    }

    @Test
    void fetchesMessageAndHistories() throws Exception {
        try (MockSidecar sidecar = new MockSidecar()) {
            sidecar.route(Map.of(
                "POST /sveda/message", request -> MockSidecar.Response.json(200, """
                    {"explanation":"Hello","tokens_used":12,"chat_id":"chat-1"}
                    """),
                "GET /sveda/chat-histories", request -> MockSidecar.Response.json(200, """
                    {"histories":[]}
                    """),
                "GET /sveda/chat-histories/chat-1", request -> MockSidecar.Response.json(200, """
                    {"history":{"chatId":"chat-1"}}
                    """),
                "PATCH /sveda/chat-histories/chat-1", request -> MockSidecar.Response.json(200, """
                    {"success":true}
                    """),
                "DELETE /sveda/chat-histories/chat-1", request -> MockSidecar.Response.json(200, """
                    {"success":true}
                    """)
            ));

            SvedaClient client = SvedaClient.builder()
                .baseUrl(sidecar.baseUrl())
                .embedToken("embed-token")
                .build();

            MessageResponse message = client.chat().create(Map.of(
                "messages", List.of(Map.of("role", "user", "content", "Hello")),
                "chatId", "chat-1"
            ));
            assertEquals("Hello", message.explanation());
            assertEquals(12, message.tokensUsed());
            assertEquals("chat-1", message.chatId());
            assertEquals("embed-token", sidecar.requests().get(0).embedToken());

            Map<String, Object> histories = client.histories().list();
            assertTrue(histories.containsKey("histories"));
            assertEquals("GET", sidecar.requests().get(1).method());
            assertEquals("/sveda/chat-histories", sidecar.requests().get(1).path());
            assertEquals("embed-token", sidecar.requests().get(1).embedToken());

            Map<String, Object> history = client.histories().get("chat-1");
            assertTrue(history.containsKey("history"));

            Map<String, Object> renamed = client.histories().rename("chat-1", "New title");
            assertEquals(true, renamed.get("success"));
            assertTrue(sidecar.requests().get(3).body().contains("\"title\":\"New title\""));

            Map<String, Object> deleted = client.histories().delete("chat-1");
            assertEquals(true, deleted.get("success"));
            assertEquals("DELETE", sidecar.lastRequest().method());
        }
    }

    @Test
    void rejectsUnauthorizedHostKey() throws Exception {
        try (MockSidecar sidecar = new MockSidecar()) {
            sidecar.respondJson(401, "{\"message\":\"Unauthorized\"}");

            SvedaClient client = SvedaClient.builder()
                .baseUrl(sidecar.baseUrl())
                .hostApiKey("wrong")
                .build();

            assertThrows(AuthenticationException.class, () -> client.embed().createToken("spring-playground"));
        }
    }
}
