# sveda-java-sdk

Java SDK for the [Sveda](https://sveda.dev) sidecar HTTP API.

Docs: [sveda.dev/docs/hosts/java](https://sveda.dev/docs/hosts/java)

Maven: `ai.sveda:sveda-java-sdk`

## Install

```xml
<dependency>
  <groupId>ai.sveda</groupId>
  <artifactId>sveda-java-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

From a local checkout:

```bash
mvn install
```

## Sidecar client

```java
SvedaClient client = SvedaClient.builder()
    .baseUrl("https://sveda.example.com")
    .hostApiKey(hostKey)
    .build();

EmbedToken token = client.embed().createToken("user-1");
HostSession session = SvedaClient.startHostSession(client, "user-1");
```

## Host integration (embed session + MCP tools)

The `ai.sveda.host` package mirrors the Laravel SDK: mint an embed token with `host_mcp_url` / `host_mcp_token`, and expose `POST /mcp/sveda` for the sidecar to list and call your tools.

```java
SvedaHost host = SvedaHost.create(new HostConfig()
    .baseUrl(System.getenv("SVEDA_CLIENT_BASE_URL"))
    .hostApiKey(System.getenv("SVEDA_CLIENT_HOST_API_KEY"))
    .mcpUrl(System.getenv("SVEDA_CLIENT_MCP_URL"))
    .serverName("My App")
    .instructions("Tools for the current user."));

host.resolveToolsUsing(() -> List.of(new SearchPostsTool()));

// Spring: map POST /mcp/sveda to host.serve(new McpHttpRequest(...))
HostSession session = host.startSession(Map.of("id", userId), requestOrigin);
```

By default, `SvedaHost` mints opaque MCP bearer tokens with an in-memory store (fine for development). Override with `mintTokenUsing` and `verifyBearerUsing` for production auth.

Implement `HostTool` with `name`, `description`, `inputSchema`, `mode`, `domain`, and `handle`.

## License

GNU Affero General Public License v3.0. See [LICENSE](LICENSE).
