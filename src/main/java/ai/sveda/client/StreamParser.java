package ai.sveda.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ai.sveda.client.exceptions.TransporterException;

public final class StreamParser {
    public static final String SSE_DONE_LINE = "data: [DONE]";

    private static final Set<String> STREAM_EVENTS = Set.of(
        "message.start",
        "text.delta",
        "reasoning.delta",
        "tool.call",
        "tool.result",
        "tool.progress",
        "context.usage",
        "chat.title",
        "max_steps",
        "message.end",
        "error"
    );

    public List<StreamEvent> iterate(String body) {
        List<StreamEvent> events = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return events;
        }
        for (String line : body.split("\\r\\n|\\n|\\r")) {
            StreamEvent event = parseLine(line);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    public List<StreamEvent> iterate(InputStream body) {
        List<StreamEvent> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                StreamEvent event = parseLine(line);
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (IOException exception) {
            throw new TransporterException(exception.getMessage(), null, exception);
        }
        return events;
    }

    public StreamEvent parseLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return null;
        }
        String payload = trimmed.substring(5).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return null;
        }
        Map<String, Object> decoded = Json.parseObjectOrNull(payload);
        if (decoded == null) {
            return null;
        }
        Object typeValue = decoded.get("type");
        if (!(typeValue instanceof String type) || !STREAM_EVENTS.contains(type)) {
            return null;
        }
        return new StreamEvent(type, decoded);
    }
}
