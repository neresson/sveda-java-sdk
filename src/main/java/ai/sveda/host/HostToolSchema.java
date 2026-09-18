package ai.sveda.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HostToolSchema {
    private HostToolSchema() {
    }

    public static Map<String, Object> object(Map<String, Map<String, Object>> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> normalized = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
            Map<String, Object> definition = new LinkedHashMap<>(entry.getValue());
            Object requiredFlag = definition.remove("required");
            normalized.put(entry.getKey(), definition);
            if (Boolean.TRUE.equals(requiredFlag)) {
                required.add(entry.getKey());
            }
        }
        schema.put("properties", normalized);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
