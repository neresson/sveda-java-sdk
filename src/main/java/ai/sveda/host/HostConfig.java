package ai.sveda.host;

import java.time.Duration;

public final class HostConfig {
    private String baseUrl = "";
    private String hostApiKey = "";
    private String mcpUrl = "";
    private String mcpPath = "/mcp/sveda";
    private String serverName = "Host Application";
    private String serverVersion = "0.1.0";
    private String instructions = "";
    private String mcpAbility = "sveda:mcp";
    private Duration tokenTtl = Duration.ofHours(1);
    private String visitorPrefix = "host";

    public String baseUrl() {
        return baseUrl;
    }

    public HostConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        return this;
    }

    public String hostApiKey() {
        return hostApiKey;
    }

    public HostConfig hostApiKey(String hostApiKey) {
        this.hostApiKey = hostApiKey == null ? "" : hostApiKey;
        return this;
    }

    public String mcpUrl() {
        return mcpUrl;
    }

    public HostConfig mcpUrl(String mcpUrl) {
        this.mcpUrl = mcpUrl == null ? "" : mcpUrl;
        return this;
    }

    public String mcpPath() {
        return mcpPath;
    }

    public HostConfig mcpPath(String mcpPath) {
        this.mcpPath = mcpPath == null || mcpPath.isBlank() ? "/mcp/sveda" : mcpPath;
        return this;
    }

    public String serverName() {
        return serverName;
    }

    public HostConfig serverName(String serverName) {
        this.serverName = serverName == null || serverName.isBlank() ? "Host Application" : serverName;
        return this;
    }

    public String serverVersion() {
        return serverVersion;
    }

    public HostConfig serverVersion(String serverVersion) {
        this.serverVersion = serverVersion == null || serverVersion.isBlank() ? "0.1.0" : serverVersion;
        return this;
    }

    public String instructions() {
        return instructions;
    }

    public HostConfig instructions(String instructions) {
        this.instructions = instructions == null ? "" : instructions;
        return this;
    }

    public String mcpAbility() {
        return mcpAbility;
    }

    public HostConfig mcpAbility(String mcpAbility) {
        this.mcpAbility = mcpAbility == null || mcpAbility.isBlank() ? "sveda:mcp" : mcpAbility;
        return this;
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }

    public HostConfig tokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()
            ? Duration.ofHours(1)
            : tokenTtl;
        return this;
    }

    public String visitorPrefix() {
        return visitorPrefix;
    }

    public HostConfig visitorPrefix(String visitorPrefix) {
        this.visitorPrefix = visitorPrefix == null || visitorPrefix.isBlank() ? "host" : visitorPrefix;
        return this;
    }
}
