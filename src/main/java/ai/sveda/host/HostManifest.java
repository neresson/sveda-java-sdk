package ai.sveda.host;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HostManifest {
    public static final String SCHEMA = "sveda.host/v1";

    private HostManifest() {
    }

    public static Map<String, Object> describe(SvedaHost host, Object user) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema", SCHEMA);
        manifest.put("sdk", Map.of(
            "language", "java",
            "version", HostManifest.class.getPackage().getImplementationVersion() == null
                ? "unknown"
                : HostManifest.class.getPackage().getImplementationVersion()
        ));
        manifest.put("subject", subject(host, user));
        manifest.put("hooks", hooks(host));
        manifest.put("tools", McpRequestHandler.listToolsFor(host, user));
        return manifest;
    }

    private static Map<String, Object> subject(SvedaHost host, Object user) {
        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("authenticated", user != null);
        subject.put("policy", user == null ? null : host.policyFor(user));
        return subject;
    }

    static Map<String, Object> hooks(SvedaHost host) {
        return host.registeredHooks();
    }
}
