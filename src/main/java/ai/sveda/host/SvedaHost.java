package ai.sveda.host;

import ai.sveda.client.HostSession;
import ai.sveda.client.SvedaClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Coordinates embed session minting and the local MCP endpoint (Laravel SDK parity).
 */
public final class SvedaHost {
    private final HostConfig config;
    private final MemoryTokenStore tokenStore;
    private Function<Object, List<HostTool>> resolveToolsUsing;
    private Function<Object, String> policyUsing;
    private Function<Object, String> visitorIdUsing;
    private Function<Object, String> mintTokenUsing;
    private Function<String, HostAuth> verifyBearerUsing;
    private Predicate<Object> authorizeUsing;
    private Function<Object, Void> afterAuthenticateUsing;
    private boolean mintTokenUsingConfigured;
    private final List<HostTool> registeredTools = new ArrayList<>();

    public SvedaHost(HostConfig config) {
        this.config = normalize(config);
        this.tokenStore = new MemoryTokenStore(this.config.tokenTtl());
        this.mintTokenUsing = this::defaultMintToken;
        this.verifyBearerUsing = token -> tokenStore.verify(token, this.config.mcpAbility());
    }

    public static SvedaHost create(HostConfig config) {
        return new SvedaHost(config);
    }

    public HostConfig config() {
        return config;
    }

    public MemoryTokenStore tokenStore() {
        return tokenStore;
    }

    public void resolveToolsUsing(Supplier<List<HostTool>> callback) {
        this.resolveToolsUsing = user -> callback.get();
    }

    public void resolveToolsUsing(Function<Object, List<HostTool>> callback) {
        this.resolveToolsUsing = callback;
    }

    public void policyUsing(Function<Object, String> callback) {
        this.policyUsing = callback;
    }

    public void visitorIdUsing(Function<Object, String> callback) {
        this.visitorIdUsing = callback;
    }

    public void mintTokenUsing(Function<Object, String> callback) {
        if (callback != null) {
            this.mintTokenUsing = callback;
            this.mintTokenUsingConfigured = true;
        }
    }

    public void verifyBearerUsing(Function<String, HostAuth> callback) {
        if (callback != null) {
            this.verifyBearerUsing = callback;
        }
    }

    public void authorizeUsing(Predicate<Object> callback) {
        this.authorizeUsing = callback;
    }

    public void afterAuthenticateUsing(Function<Object, Void> callback) {
        this.afterAuthenticateUsing = callback;
    }

    public void registerTool(HostTool tool) {
        registeredTools.add(tool);
    }

    public List<HostTool> resolveTools() {
        return resolveTools(null);
    }

    public List<HostTool> resolveTools(Object user) {
        if (resolveToolsUsing != null) {
            List<HostTool> tools = resolveToolsUsing.apply(user);
            return tools == null ? List.of() : List.copyOf(tools);
        }
        return List.copyOf(registeredTools);
    }

    public Map<String, Object> describe(Object user) {
        return HostManifest.describe(this, user);
    }

    public Map<String, Object> registeredHooks() {
        Map<String, Object> hooks = new LinkedHashMap<>();
        hooks.put("resolve_tools", resolveToolsUsing != null);
        hooks.put("policy", policyUsing != null);
        hooks.put("authorize", authorizeUsing != null);
        hooks.put("visitor_id", visitorIdUsing != null);
        hooks.put("mint_token", mintTokenUsingConfigured);
        return hooks;
    }

    public String policyFor(Object user) {
        if (policyUsing == null) {
            return null;
        }
        String value = policyUsing.apply(user);
        if (value == null) {
            return null;
        }
        String policy = value.trim();
        return policy.isEmpty() ? null : policy;
    }

    public boolean configured() {
        return !config.baseUrl().isBlank() && !config.hostApiKey().isBlank();
    }

    public String mcpPublicUrl(String requestOrigin) {
        if (!config.mcpUrl().isBlank()) {
            return trimSlash(config.mcpUrl());
        }
        String origin = trimSlash(requestOrigin);
        if (origin.isBlank()) {
            return normalizePath(config.mcpPath());
        }
        return origin + normalizePath(config.mcpPath());
    }

    public HostSession startSession(Object user) {
        return startSession(user, null);
    }

    public HostSession startSession(Object user, String requestOrigin) {
        if (!configured()) {
            throw new IllegalStateException("Set SVEDA_CLIENT_BASE_URL and SVEDA_CLIENT_HOST_API_KEY.");
        }

        String mcpToken = mintTokenUsing.apply(user);
        String visitorId = visitorId(user);
        String mcpUrl = mcpPublicUrl(requestOrigin);

        SvedaClient client = SvedaClient.builder()
            .baseUrl(config.baseUrl())
            .hostApiKey(config.hostApiKey())
            .build();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("visitor_id", visitorId);
        params.put("host_mcp_url", mcpUrl);
        params.put("host_mcp_token", mcpToken);
        String policy = policyFor(user);
        if (policy != null) {
            params.put("policy", policy);
        }
        var token = client.embed().createToken(params);
        return SvedaClient.startHostSession(client.baseUrl(), token.token(), token.expiresIn(), token.appearance());
    }

    public HostAuth authenticateBearerToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return null;
        }
        return verifyBearerUsing.apply(bearerToken.trim());
    }

    public McpHttpResponse serve(McpHttpRequest request) {
        if (!"POST".equalsIgnoreCase(request.method())) {
            return McpHttpResponse.empty(405);
        }

        HostAuth auth = authenticateBearerToken(request.bearerToken());
        if (auth == null) {
            return McpHttpResponse.empty(401);
        }

        Object user = auth.user();
        if (authorizeUsing != null && !authorizeUsing.test(user)) {
            return McpHttpResponse.empty(403);
        }
        if (afterAuthenticateUsing != null) {
            afterAuthenticateUsing.apply(user);
        }

        Map<String, String> headers = request.headers() == null ? Map.of() : request.headers();
        return McpRequestHandler.handle(this, request.body(), auth, headers);
    }

    public static String bearerTokenFromAuthorization(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return "";
        }
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "";
        }
        return authorization.substring(prefix.length()).trim();
    }

    private String visitorId(Object user) {
        if (visitorIdUsing != null) {
            return visitorIdUsing.apply(user);
        }
        String id = user instanceof Map<?, ?> map && map.get("id") != null
            ? String.valueOf(map.get("id"))
            : "anonymous";
        return config.visitorPrefix() + "-" + id;
    }

    private String defaultMintToken(Object user) {
        String userId = user instanceof Map<?, ?> map && map.get("id") != null
            ? String.valueOf(map.get("id"))
            : "anonymous";
        tokenStore.revokeForUser(userId);
        return tokenStore.mint(userId, config.mcpAbility());
    }

    private static HostConfig normalize(HostConfig config) {
        HostConfig normalized = new HostConfig()
            .baseUrl(trimSlash(config.baseUrl()))
            .hostApiKey(config.hostApiKey().trim())
            .mcpUrl(trimSlash(config.mcpUrl()))
            .mcpPath(normalizePath(config.mcpPath()))
            .serverName(config.serverName())
            .serverVersion(config.serverVersion())
            .instructions(config.instructions())
            .mcpAbility(config.mcpAbility())
            .tokenTtl(config.tokenTtl().compareTo(Duration.ofSeconds(60)) < 0 ? Duration.ofSeconds(60) : config.tokenTtl())
            .visitorPrefix(config.visitorPrefix());
        return normalized;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String normalizePath(String path) {
        String normalized = path == null || path.isBlank() ? "/mcp/sveda" : path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
