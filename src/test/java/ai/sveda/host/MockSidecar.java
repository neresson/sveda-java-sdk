package ai.sveda.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

final class MockSidecar implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpServer server;
    final String baseUrl;

    private MockSidecar(HttpServer server) {
        this.server = server;
        this.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @SuppressWarnings("unchecked")
    static MockSidecar start(Map<String, Object> captured) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sveda", exchange -> {
            if (!"/sveda/embed/token".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.putAll(MAPPER.readValue(body, Map.class));
            byte[] response = """
                {"token":"embed-token","visitor_id":"go-playground","expires_in":3600}
                """.trim().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(response);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return new MockSidecar(server);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
