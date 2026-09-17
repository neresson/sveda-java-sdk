package ai.sveda.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SvedaClient {
    private final String baseUrl;
    private final HttpTransport transport;

    private SvedaClient(String baseUrl, HttpTransport transport) {
        this.baseUrl = baseUrl;
        this.transport = transport;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static HostSession startHostSession(String origin, String token, int expiresIn, Map<String, Object> appearance) {
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("origin is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Sidecar returned an empty embed token.");
        }
        return new HostSession(trimSlash(origin), token, expiresIn, appearance);
    }

    public static HostSession startHostSession(SvedaClient client, String visitorId) {
        EmbedToken token = client.embed().createToken(visitorId);
        return startHostSession(client.baseUrl(), token.token(), token.expiresIn(), token.appearance());
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Embed embed() {
        return new Embed(transport);
    }

    public Chat chat() {
        return new Chat(transport);
    }

    public Histories histories() {
        return new Histories(transport);
    }

    static String trimSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static final class Builder {
        private String baseUrl = "";
        private String hostApiKey;
        private String embedToken;
        private Duration timeout = Duration.ofSeconds(30);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private HttpClient httpClient;
        private final Map<String, String> headers = new LinkedHashMap<>();

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
            return this;
        }

        public Builder hostApiKey(String hostApiKey) {
            this.hostApiKey = blankToNull(hostApiKey);
            return this;
        }

        public Builder embedToken(String embedToken) {
            this.embedToken = blankToNull(embedToken);
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public SvedaClient build() {
            String origin = trimSlash(baseUrl);
            Map<String, String> defaultHeaders = new LinkedHashMap<>(headers);
            if (hostApiKey != null) {
                defaultHeaders.put("Authorization", "Bearer " + hostApiKey);
            }
            if (embedToken != null) {
                defaultHeaders.put("X-Sveda-Embed-Token", embedToken);
            }
            HttpClient client = httpClient != null
                ? httpClient
                : HttpClient.newBuilder().connectTimeout(connectTimeout).build();
            return new SvedaClient(origin, new HttpTransport(client, origin, defaultHeaders, timeout));
        }

        private static String blankToNull(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value;
        }
    }
}
