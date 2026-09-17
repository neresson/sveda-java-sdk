package ai.sveda.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

final class MockSidecar implements AutoCloseable {
    record RecordedRequest(String method, String path, String authorization, String embedToken, String body) {
    }

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private Function<RecordedRequest, Response> handler = request -> Response.json(200, "{}");

    MockSidecar() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sveda", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<RecordedRequest> requests() {
        return new ArrayList<>(requests);
    }

    RecordedRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    void onJson(Function<RecordedRequest, Response> handler) {
        this.handler = handler;
    }

    void respondJson(int status, String body) {
        this.handler = request -> Response.json(status, body);
    }

    void respondStream(String body) {
        this.handler = request -> Response.stream(200, body);
    }

    void route(Map<String, Function<RecordedRequest, Response>> routes) {
        this.handler = request -> {
            Function<RecordedRequest, Response> match = routes.get(request.method() + " " + request.path());
            if (match == null) {
                return Response.json(404, "{\"message\":\"not found\"}");
            }
            return match.apply(request);
        };
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RecordedRequest request = new RecordedRequest(
            exchange.getRequestMethod().toUpperCase(Locale.ROOT),
            exchange.getRequestURI().getPath(),
            header(exchange, "Authorization"),
            header(exchange, "X-Sveda-Embed-Token"),
            body
        );
        requests.add(request);
        Response response = handler.apply(request);
        exchange.getResponseHeaders().set("Content-Type", response.contentType);
        byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    record Response(int status, String contentType, String body) {
        static Response json(int status, String body) {
            return new Response(status, "application/json", body);
        }

        static Response stream(int status, String body) {
            return new Response(status, HttpTransport.STREAM_ACCEPT, body);
        }
    }
}
