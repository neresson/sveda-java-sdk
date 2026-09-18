package ai.sveda.host;

import java.util.Map;

/**
 * Host MCP tool the Sveda sidecar can list and call.
 */
public interface HostTool {
    String name();

    String description();

    Map<String, Object> inputSchema();

    String mode();

    String domain();

    Object handle(Map<String, Object> arguments, HostCallContext context) throws Exception;
}
