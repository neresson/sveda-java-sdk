package ai.sveda.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class Histories {
    private final HttpTransport transport;

    Histories(HttpTransport transport) {
        this.transport = transport;
    }

    public Map<String, Object> list() {
        return transport.requestJson("GET", "/sveda/chat-histories", Map.of());
    }

    public Map<String, Object> get(String chatId) {
        return transport.requestJson("GET", historyPath(chatId), Map.of());
    }

    public Map<String, Object> rename(String chatId, String title) {
        return transport.requestJson("PATCH", historyPath(chatId), Map.of("title", title));
    }

    public Map<String, Object> delete(String chatId) {
        return transport.requestJson("DELETE", historyPath(chatId), Map.of());
    }

    private static String historyPath(String chatId) {
        return "/sveda/chat-histories/" + URLEncoder.encode(chatId, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
