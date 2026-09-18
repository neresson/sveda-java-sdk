package ai.sveda.host;

import java.util.Map;

public record HostCallContext(
    Object user,
    Map<String, Object> pageContext,
    String chatId
) {
    public static HostCallContext anonymous() {
        return new HostCallContext(null, null, null);
    }
}
