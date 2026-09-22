package ai.sveda.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void locksSidecarContractSurface() throws Exception {
        Path path = Path.of("contracts", "sidecar.v1.json");
        if (!Files.exists(path)) {
            path = Path.of("..", "sveda", "packages", "protocol", "contracts", "sidecar.v1.json");
        }
        JsonNode contract = MAPPER.readTree(Files.readString(path));
        assertEquals("1.0", contract.get("version").asText());
        assertEquals("/sveda", contract.get("prefix").asText());
        assertEquals(
                "application/vnd.sveda.stream+json",
                contract.get("accept").get("svedaStream").asText()
        );
        Set<String> routes = new HashSet<>();
        for (JsonNode route : contract.get("routes")) {
            routes.add(route.get("method").asText() + " " + route.get("path").asText());
        }
        assertTrue(routes.contains("POST /sveda/stream"));
        assertTrue(routes.contains("POST /sveda/message"));
        assertTrue(routes.contains("POST /sveda/embed/token"));
    }
}
