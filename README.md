# LLM Client Framework for Java

Unified Java client for working with local and cloud LLM providers through one API.

[![Maven Central](https://img.shields.io/badge/Maven-in.simpletools%3Allm--client--framework-2ea44f)](https://central.sonatype.com/artifact/in.simpletools/llm-client-framework)
[![Java](https://img.shields.io/badge/Java-21+-1f6feb)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

## What This Library Gives You

- One client API across Ollama, OpenAI-compatible providers, Claude, Groq, Mistral, OpenRouter, and more
- Simple sync, async, and streaming chat APIs
- Lambda-based tool registration
- Annotation-based tool auto-registration with `@LLMTool`
- In-memory or Redis-backed conversation history
- Built-in file, web, shell, and HTTP tools
- Maven Central-ready coordinates for the `in.simpletools` namespace

## Supported Providers

| Category | Providers |
|----------|-----------|
| Local | Ollama, LM Studio, vLLM, Jan |
| Cloud | OpenAI, Claude, DeepSeek, NVIDIA NIM, Groq, Mistral, OpenRouter |

## Install

### Gradle

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.2'
}
```

### Maven

```xml
<dependency>
    <groupId>in.simpletools</groupId>
    <artifactId>llm-client-framework</artifactId>
    <version>1.0.2</version>
</dependency>
```

## Integrate In A New Project

### 1. Create a Java 21 project

This library targets Java 21. A minimal Gradle app is enough:

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.2'
}

application {
    mainClass = 'demo.Main'
}
```

### 2. Add a first local-provider example

Create `src/main/java/demo/Main.java`:

```java
package demo;

import in.simpletools.llm.framework.client.LLMClient;

public class Main {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest");
        String reply = client.chat("Explain recursion in simple words.");
        System.out.println(reply);
    }
}
```

### 3. Run it

```bash
./gradlew run
```

### 4. Local setup for Ollama

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull gemma4:latest
ollama serve
```

If you prefer a cloud model instead of Ollama, switch to one of the provider factory methods below.

## Integrate In An Existing Project

Most teams do not need to restructure their app. Add the dependency, create a small wrapper/service, and keep the rest of your code calling that service.

### Plain Java service example

```java
import in.simpletools.llm.framework.client.LLMClient;

public class AiService {
    private final LLMClient client;

    public AiService() {
        this.client = LLMClient.openAI(
            "gpt-4o-mini",
            System.getenv("OPENAI_API_KEY")
        );
    }

    public String summarize(String text) {
        return client.chat("Summarize this text:\n\n" + text);
    }
}
```

### Spring Boot integration example

```java
import in.simpletools.llm.framework.client.LLMClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Bean
    LLMClient llmClient() {
        return LLMClient.openAI(
            "gpt-4o-mini",
            System.getenv("OPENAI_API_KEY")
        );
    }
}
```

```java
import in.simpletools.llm.framework.client.LLMClient;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {
    private final LLMClient llmClient;

    public SummaryService(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    public String summarizeTicket(String ticket) {
        return llmClient.chat("Summarize this support ticket:\n\n" + ticket);
    }
}
```

### Migration approach for an existing codebase

1. Add the dependency from Maven Central.
2. Create one `LLMClient` bean or singleton.
3. Hide provider choice behind your own service interface.
4. Move prompts into dedicated service methods instead of controllers.
5. Add tools only where they are genuinely needed.
6. Start with one user flow, then expand.

## Provider Examples

### Ollama

```java
LLMClient client = LLMClient.ollama("gemma4:latest");
```

### OpenAI

```java
LLMClient client = LLMClient.openAI(
    "gpt-4o-mini",
    System.getenv("OPENAI_API_KEY")
);
```

### Claude

```java
LLMClient client = LLMClient.claude(
    "claude-3-5-sonnet-20241022",
    System.getenv("ANTHROPIC_API_KEY")
);
```

### Groq

```java
LLMClient client = LLMClient.groq(
    "llama-3.1-70b-versatile",
    System.getenv("GROQ_API_KEY")
);
```

### Builder API

```java
import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.config.ClientConfig;
import in.simpletools.llm.framework.config.Provider;
import in.simpletools.llm.framework.history.ConversationHistory;

LLMClient client = LLMClient.builder()
    .config(
        ClientConfig.of(Provider.OPENAI)
            .model("gpt-4o-mini")
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .timeoutSeconds(60)
    )
    .history(new ConversationHistory(50))
    .build();
```

## Tool Calling

### Register a simple lambda tool

```java
import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.tool.ToolRegistry;
import java.util.Map;

LLMClient client = LLMClient.ollama("gemma4:latest");

client.tool(
    "calculate",
    "Evaluate a math expression",
    args -> {
        String expression = args.get("expression").toString();
        return expression + " = 42";
    },
    Map.of(
        "expression",
        new ToolRegistry.ParamInfo(
            "expression",
            "Expression to evaluate",
            true,
            String.class
        )
    )
);

String reply = client.chat("Use the calculate tool for 25 * 4 + 10.");
```

### Annotation-driven tools

```java
import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.tool.LLMTool;
import in.simpletools.llm.framework.tool.ToolParam;

class BusinessTools {
    @LLMTool(name = "ticket_status", description = "Get ticket status")
    public String ticketStatus(@ToolParam("id") String id) {
        return "Ticket " + id + " is IN_PROGRESS";
    }
}

LLMClient client = LLMClient.openAI("gpt-4o-mini", System.getenv("OPENAI_API_KEY"));
client.registerTools(new BusinessTools());
```

## Built-In Tools

### System tools

```java
LLMClient client = LLMClient.ollama("gemma4:latest")
    .withSystemTools();
```

This enables file, web, and shell tools such as:

- `read_file`
- `write_file`
- `list_dir`
- `find_files`
- `grep`
- `web_search`
- `fetch_webpage`
- `run_bash`

### HTTP tools

```java
LLMClient client = LLMClient.openAI("gpt-4o-mini", System.getenv("OPENAI_API_KEY"))
    .withHttpTools();
```

This enables:

- `http_get`
- `http_post`
- `http_put`
- `http_patch`
- `http_delete`

## Async, Streaming, and History

### Async

```java
CompletableFuture<String> future = client.chatAsync("Write a short release note.");
System.out.println(future.join());
```

### Streaming

```java
client.streamChat(
    "Count from 1 to 5",
    token -> System.out.print(token),
    error -> System.err.println(error)
);
```

### Redis-backed history

```java
LLMClient client = LLMClient.openAI("gpt-4o-mini", System.getenv("OPENAI_API_KEY"))
    .withRedisHistory("user-123");
```

## Publish This Library To Maven Central

This repository already uses the `in.simpletools` group.

### Coordinates

```text
groupId    = in.simpletools
artifactId = llm-client-framework
```

### What you need before publishing

1. A verified `in.simpletools` namespace in Maven Central Portal
2. A Central Portal user token
3. A GPG key for signing release artifacts
4. Java 21 available in your CI or local environment

### Credentials

Create a Maven Central Portal user token and export it in your environment:

```bash
export CENTRAL_PORTAL_USERNAME=your_token_username
export CENTRAL_PORTAL_PASSWORD=your_token_password
```

If your build still uses older variable names, map the same values:

```bash
export OSSRH_USERNAME="$CENTRAL_PORTAL_USERNAME"
export OSSRH_PASSWORD="$CENTRAL_PORTAL_PASSWORD"
```

### Snapshot publishing

Use a version ending in `-SNAPSHOT`, for example:

```groovy
version = '1.0.3-SNAPSHOT'
```

Then publish:

```bash
./gradlew publish
```

### Release publishing

Use a release version:

```groovy
version = '1.0.3'
```

Then publish:

```bash
./gradlew publish
```

### Recommended release checklist

1. Update `version` in `build.gradle`
2. Confirm `group = 'in.simpletools'`
3. Confirm POM metadata is present
4. Generate and sign artifacts
5. Run `./gradlew publish`
6. Verify the deployment in Maven Central Portal
7. Tag the release in GitHub

## Project Structure

```text
src/main/java/in/simpletools/llm/framework/
├── adapter/
├── client/
├── config/
├── history/
├── model/
├── tool/
├── tools/
└── utils/
```

## Troubleshooting

### `Connection refused` with Ollama

```bash
ollama serve
curl http://localhost:11434/api/tags
```

### `401 Unauthorized` with cloud providers

Check that the correct API key environment variable is set before starting the app.

### `Tool not called`

- Write a clear tool description
- Keep parameter names obvious
- Use a model that supports tool calling well

### `publish` fails

Check:

- namespace ownership for `in.simpletools`
- Central Portal token username and password
- required POM metadata
- Java 21 availability in CI/local environment

## License

MIT. See [LICENSE](LICENSE).
