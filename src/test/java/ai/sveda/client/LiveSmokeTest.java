package ai.sveda.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LiveSmokeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void runsHealthMessageStreamAndHistoryFlow() throws Exception {
        String baseUrl = System.getenv("SVEDA_BASE_URL");
        String hostKey = System.getenv("SVEDA_HOST_KEY");
        Assumptions.assumeTrue(baseUrl != null && !baseUrl.isBlank());
        Assumptions.assumeTrue(hostKey != null && !hostKey.isBlank());

        HttpClient http = HttpClient.newHttpClient();
        for (String path : List.of("/sveda/health", "/sveda/ready")) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
            JsonNode payload = MAPPER.readTree(response.body());
            assertTrue(payload.path("ok").asBoolean(false));
        }

        SvedaClient host = SvedaClient.builder()
                .baseUrl(baseUrl)
                .hostApiKey(hostKey)
                .build();
        EmbedToken token = host.embed().createToken("sdk-compat-java");
        assertTrue(token.token().startsWith("sveda_embed_"));

        SvedaClient embed = SvedaClient.builder()
                .baseUrl(baseUrl)
                .embedToken(token.token())
                .build();
        List<StreamEvent> events = embed.chat().createStreamed(Map.of(
                "prompt", "compat stream",
                "chatId", "sdk-compat-java",
                "messages", List.of(Map.of("id", "m1", "role", "user", "content", "compat stream"))
        ));
        assertFalse(events.isEmpty());

        MessageResponse message = embed.chat().create(Map.of(
                "prompt", "compat smoke",
                "chatId", "sdk-compat-java-json",
                "messages", List.of(Map.of("id", "m2", "role", "user", "content", "compat smoke"))
        ));
        assertFalse(message.explanation().isBlank());

        Map<String, Object> histories = embed.histories().list();
        assertNotNull(histories.get("histories"));
    }
}
