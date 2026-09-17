package ai.sveda.client;

import java.util.Map;

public final class MessageResponse {
    private final Map<String, Object> payload;

    public MessageResponse(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String explanation() {
        return stringValue(payload.get("explanation"));
    }

    public int tokensUsed() {
        Object value = payload.get("tokens_used");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public String chatId() {
        return stringValue(payload.get("chat_id"));
    }

    public Map<String, Object> payload() {
        return payload;
    }

    static MessageResponse from(Map<String, Object> payload) {
        return new MessageResponse(payload);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
