package ai.sveda.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record HostSession(
    String origin,
    String token,
    @JsonProperty("expires_in") int expiresIn,
    Map<String, Object> appearance
) {
}
