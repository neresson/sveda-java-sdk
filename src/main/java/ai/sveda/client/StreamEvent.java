package ai.sveda.client;

import java.util.Map;

public final class StreamEvent {
    private final String type;
    private final Map<String, Object> payload;

    public StreamEvent(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
    }

    public String type() {
        return type;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public Object get(String name) {
        return payload.get(name);
    }

    public Map<String, Object> toMap() {
        return payload;
    }
}
