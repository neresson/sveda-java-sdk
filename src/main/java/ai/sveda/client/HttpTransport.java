package ai.sveda.client;

import ai.sveda.client.exceptions.AuthenticationException;
import ai.sveda.client.exceptions.ErrorException;
import ai.sveda.client.exceptions.TransporterException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class HttpTransport {
    static final String STREAM_ACCEPT = "application/vnd.sveda.stream+json";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Duration timeout;

    HttpTransport(HttpClient httpClient, String baseUrl, Map<String, String> defaultHeaders, Duration timeout) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.defaultHeaders = Map.copyOf(defaultHeaders);
        this.timeout = timeout;
    }

    Map<String, Object> requestJson(String method, String path, Map<String, Object> payload) {
        boolean hasBody = payload != null && !payload.isEmpty() && sendsBody(method);
        HttpRequest.Builder builder = requestBuilder(method, path, hasBody ? Json.stringify(payload) : (sendsBody(method) ? "{}" : null));
        builder.header("Accept", "application/json");
        if (sendsBody(method)) {
            builder.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return decodeResponse(response.statusCode(), response.body());
    }

    InputStream requestStream(String method, String path, Map<String, Object> payload) {
        HttpRequest.Builder builder = requestBuilder(method, path, Json.stringify(payload == null ? Map.of() : payload));
        builder.header("Accept", STREAM_ACCEPT);
        builder.header("Content-Type", "application/json");
        HttpResponse<InputStream> response = send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            closeQuietly(response.body());
            throw new AuthenticationException("Sveda API authentication failed with status " + status);
        }
        if (status < 200 || status >= 300) {
            String body = readAll(response.body());
            Map<String, Object> decoded = Json.parseObjectOrNull(body);
            throw new ErrorException(errorMessage(decoded, status), status, decoded);
        }
        return response.body();
    }

    private HttpRequest.Builder requestBuilder(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path)).timeout(timeout);
        defaultHeaders.forEach(builder::header);
        HttpRequest.BodyPublisher publisher = body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
        builder.method(method.toUpperCase(Locale.ROOT), publisher);
        return builder;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransporterException(exception.getMessage(), null, exception);
        } catch (IOException exception) {
            throw new TransporterException(exception.getMessage(), null, exception);
        }
    }

    private URI resolve(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        String suffix = path.startsWith("/") ? path : "/" + path;
        return URI.create(baseUrl + suffix);
    }

    private Map<String, Object> decodeResponse(int status, String body) {
        if (status == 401 || status == 403) {
            throw new AuthenticationException("Sveda API authentication failed with status " + status);
        }
        if (status < 200 || status >= 300) {
            Map<String, Object> decoded = Json.parseObjectOrNull(body);
            throw new ErrorException(errorMessage(decoded, status), status, decoded);
        }
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        return Json.parseObject(body);
    }

    private static String errorMessage(Map<String, Object> decoded, int status) {
        if (decoded != null) {
            Object message = decoded.get("message");
            if (message instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return "Sveda API request failed with status " + status;
    }

    private static boolean sendsBody(String method) {
        String upper = method.toUpperCase(Locale.ROOT);
        return "POST".equals(upper) || "PUT".equals(upper) || "PATCH".equals(upper);
    }

    private static String readAll(InputStream stream) {
        try (InputStream input = stream) {
            return new String(input.readAllBytes());
        } catch (IOException exception) {
            return "";
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // The caller already received an HTTP error status.
        }
    }
}
