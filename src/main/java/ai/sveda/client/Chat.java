package ai.sveda.client;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class Chat {
    private final HttpTransport transport;

    Chat(HttpTransport transport) {
        this.transport = transport;
    }

    public MessageResponse create(Map<String, Object> params) {
        return MessageResponse.from(transport.requestJson("POST", "/sveda/message", params));
    }

    public List<StreamEvent> createStreamed(Map<String, Object> params) {
        InputStream stream = transport.requestStream("POST", "/sveda/stream", params);
        return new StreamParser().iterate(stream);
    }
}
