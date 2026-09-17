package ai.sveda.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import ai.sveda.client.exceptions.UnserializableResponse;

final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private Json() {
    }

    static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new UnserializableResponse("Unable to encode Sveda API request as JSON.", exception);
        }
    }

    static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> decoded = MAPPER.readValue(json, MAP_TYPE);
            return decoded == null ? new LinkedHashMap<>() : decoded;
        } catch (JsonProcessingException exception) {
            throw new UnserializableResponse("Unable to decode Sveda API response as JSON.", exception);
        }
    }

    static Map<String, Object> parseObjectOrNull(String json) {
        try {
            return parseObject(json);
        } catch (UnserializableResponse exception) {
            return null;
        }
    }
}
