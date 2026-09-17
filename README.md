# sveda-java-sdk

Java SDK for the [Sveda AI](https://github.com/neresson/sveda) sidecar HTTP API.

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

## Usage

```java
SvedaClient client = SvedaClient.builder()
    .baseUrl("https://sveda.example.com")
    .hostApiKey(hostKey)
    .build();

EmbedToken token = client.embed().createToken("user-1");
HostSession session = SvedaClient.startHostSession(client, "user-1");
```

## License

MIT
