package ai.sveda.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StreamParserTest {
    @Test
    void parsesStreamEventsAndStopsOnDone() {
        String content = String.join("\n", List.of(
            ": connected",
            "",
            "data: {\"type\":\"message.start\"}",
            "",
            "data: {\"type\":\"text.delta\",\"delta\":\"Hello\"}",
            "",
            "data: {\"type\":\"message.end\",\"finishReason\":\"stop\"}",
            "",
            "data: [DONE]",
            ""
        ));

        List<StreamEvent> events = new StreamParser().iterate(content);

        assertEquals(3, events.size());
        assertEquals("message.start", events.get(0).type());
        assertEquals("text.delta", events.get(1).type());
        assertEquals("Hello", events.get(1).get("delta"));
        assertEquals("message.end", events.get(2).type());
        assertEquals(StreamParser.SSE_DONE_LINE, "data: [DONE]");
    }

    @Test
    void ignoresInvalidLines() {
        String content = "event: ping\ndata: not-json\ndata: {\"type\":\"unknown.event\"}\n";

        List<StreamEvent> events = new StreamParser().iterate(content);

        assertTrue(events.isEmpty());
    }
}
