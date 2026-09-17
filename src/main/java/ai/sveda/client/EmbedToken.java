package ai.sveda.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record EmbedToken(
    String token,
    @JsonProperty("visitor_id") String visitorId,
    @JsonProperty("expires_in") int expiresIn,
    Map<String, Object> appearance
) {
    @SuppressWarnings("unchecked")
    static EmbedToken from(Map<String, Object> payload) {
        Object appearanceValue = payload.get("appearance");
        Map<String, Object> appearance = appearanceValue instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : null;
        return new EmbedToken(
            stringValue(payload.get("token")),
            stringValue(payload.get("visitor_id")),
            Math.max(60, intValue(payload.get("expires_in"), 3600)),
            appearance
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
