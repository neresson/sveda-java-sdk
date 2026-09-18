package ai.sveda.host;

import java.util.Map;

public record HostAuth(String userId) {
    public Object user() {
        return Map.of("id", userId);
    }
}
