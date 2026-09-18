package ai.sveda.host;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory MCP bearer token store for development and simple hosts.
 */
public final class MemoryTokenStore {
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();

    public MemoryTokenStore(Duration ttl) {
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(1) : ttl;
    }

    public String mint(String userId) {
        return mint(userId, mcpAbilityDefault());
    }

    public String mint(String userId, String ability) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        Instant expires = Instant.now().plus(ttl);
        tokens.put(token, new TokenRecord(userId == null ? "anonymous" : userId, ability, expires));
        pruneExpired();
        return token;
    }

    public void revokeForUser(String userId) {
        if (userId == null) {
            return;
        }
        Iterator<Map.Entry<String, TokenRecord>> iterator = tokens.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TokenRecord> entry = iterator.next();
            if (userId.equals(entry.getValue().userId)) {
                iterator.remove();
            }
        }
    }

    public HostAuth verify(String token, String requiredAbility) {
        if (token == null || token.isBlank()) {
            return null;
        }
        pruneExpired();
        TokenRecord record = tokens.get(token);
        if (record == null || Instant.now().isAfter(record.expires)) {
            return null;
        }
        if (requiredAbility != null && !requiredAbility.isBlank() && !requiredAbility.equals(record.ability)) {
            return null;
        }
        return new HostAuth(record.userId);
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expires));
    }

    private static String mcpAbilityDefault() {
        return "sveda:mcp";
    }

    private record TokenRecord(String userId, String ability, Instant expires) {
    }
}
