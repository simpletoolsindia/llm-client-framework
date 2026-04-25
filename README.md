# LLM Client Framework for Java

Unified Java client for local and cloud LLM providers with one API, built-in tool calling, conversation history, context tracking, and automatic history compaction.

[![Maven Central](https://img.shields.io/badge/Maven-in.simpletools%3Allm--client--framework-2ea44f)](https://central.sonatype.com/artifact/in.simpletools/llm-client-framework)
[![Java](https://img.shields.io/badge/Java-21+-1f6feb)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

## Version

Current release:

```text
in.simpletools:llm-client-framework:1.0.4
```

## What This Framework Gives You

- One client API across Ollama, OpenAI-compatible providers, Claude, Groq, Mistral, OpenRouter, and more
- Sync, async, and streaming chat APIs
- Lambda-based and annotation-based tool calling
- In-memory or Redis-backed conversation history
- Built-in system and HTTP tools
- Context window tracking with visible used and remaining context
- Best-effort automatic context window detection from model names
- Automatic conversation compaction when the context window gets too full
- Rolling summary preservation so important conversation state survives compaction

## New Context Features

The framework now supports automatic context management.

### Automatic context window detection

The framework detects common model context sizes automatically:

```java
LLMClient client = LLMClient.ollama("gemma4:latest");
System.out.println(client.getContextInfo().summary());
```

If you want to force a known value:

```java
client.withContextWindow(128000);
```

### Context usage visibility

```java
var info = client.getContextInfo();
System.out.println(info.summary());
System.out.println("Used: " + info.usedTokens());
System.out.println("Remaining: " + info.remainingTokens());
System.out.println("Usage %: " + info.usagePercent());
```

You can also project usage before sending the next turn:

```java
var projected = client.getProjectedContextInfo("Summarize the entire discussion.");
System.out.println(projected.summary());
```

### Automatic compaction

When the conversation gets too large, the framework can:

1. ask the model to compress the conversation
2. keep durable facts, decisions, constraints, and unresolved work
3. replace older history with a compacted summary
4. keep the most recent live turns

Enable it like this:

```java
LLMClient client = LLMClient.ollama("gemma4:latest")
    .withAutoCompaction();
```

Or tune it:

```java
LLMClient client = LLMClient.openAI("gpt-4o-mini", System.getenv("OPENAI_API_KEY"))
    .withAutoCompaction(85.0, 55.0, 6);
```

Meaning:

- start compacting at `85%` usage
- compact until usage is around `55%`
- keep the last `6` recent non-system messages in full

You can inspect the current rolling summary:

```java
System.out.println(client.getCompactedContextSummary());
```

Or compact manually:

```java
client.compactHistoryNow();
```

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
    implementation 'in.simpletools:llm-client-framework:1.0.4'
}
```

### Maven

```xml
<dependency>
    <groupId>in.simpletools</groupId>
    <artifactId>llm-client-framework</artifactId>
    <version>1.0.4</version>
</dependency>
```

## Use In A New Project

### 1. Create a Java 21 Gradle app

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.4'
}

application {
    mainClass = 'demo.Main'
}
```

### 2. Add a basic main class

```java
package demo;

import in.simpletools.llm.framework.client.LLMClient;

public class Main {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest")
            .withAutoCompaction();

        String reply = client.chat("Explain recursion in simple words.");
        System.out.println(reply);
        System.out.println(client.getContextInfo().summary());
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

## Use In An Existing Project

Most teams should integrate this through one small service layer instead of calling the LLM directly from every controller or endpoint.

### Plain Java service example

```java
import in.simpletools.llm.framework.client.LLMClient;

public class AiService {
    private final LLMClient client;

    public AiService() {
        this.client = LLMClient.openAI(
            "gpt-4o-mini",
            System.getenv("OPENAI_API_KEY")
        ).withAutoCompaction(85.0, 55.0, 6);
    }

    public String summarize(String text) {
        return client.chat("Summarize this text:\n\n" + text);
    }

    public String contextStats() {
        return client.getContextInfo().summary();
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
        ).withAutoCompaction(85.0, 55.0, 6);
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

    public String getContextUsage() {
        return llmClient.getContextInfo().summary();
    }
}
```

### Recommended migration approach

1. Add the dependency from Maven Central.
2. Create one shared `LLMClient` bean or singleton.
3. Wrap it in your own service class.
4. Turn on auto-compaction for long-running chat flows.
5. Surface `getContextInfo()` in logs or admin dashboards.
6. Add tools only to flows that really need them.

## Provider Examples

### Ollama

```java
LLMClient client = LLMClient.ollama("gemma4:latest")
    .withAutoCompaction();
```

### OpenAI

```java
LLMClient client = LLMClient.openAI(
    "gpt-4o-mini",
    System.getenv("OPENAI_API_KEY")
).withAutoCompaction(85.0, 55.0, 6);
```

### Claude

```java
LLMClient client = LLMClient.claude(
    "claude-3-5-sonnet-20241022",
    System.getenv("ANTHROPIC_API_KEY")
).withAutoCompaction();
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
    .build()
    .withAutoCompaction(85.0, 55.0, 6);
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

This enables:

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
    .withRedisHistory("user-123")
    .withAutoCompaction();
```

## Example Project

A standalone example project is included here:

- [examples/auto-compaction-demo](examples/auto-compaction-demo/README.md)

It shows:

- local Ollama usage
- automatic context tracking
- auto-compaction configuration
- printing current context stats and compacted summary

## Publish This Library To Maven Central

This repository publishes under:

```text
groupId    = in.simpletools
artifactId = llm-client-framework
```

### Release requirements

1. Verified `in.simpletools` namespace in Maven Central Portal
2. Central Portal user token
3. GPG signing key
4. Java 21 in local or CI environment

### Environment variables

```bash
export CENTRAL_PORTAL_USERNAME=your_token_username
export CENTRAL_PORTAL_PASSWORD=your_token_password
export ORG_GRADLE_PROJECT_signingKey='ASCII_ARMORED_GPG_PRIVATE_KEY'
export ORG_GRADLE_PROJECT_signingPassword='your_gpg_passphrase'
```

### Release

```bash
./gradlew publish
```

For OSSRH compatibility handoff:

```bash
curl -X POST \
  -H "Authorization: Bearer <base64(username:password)>" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/in.simpletools?publishing_type=automatic"
```

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
├── utils/
└── example/

examples/
└── auto-compaction-demo/
```

## Troubleshooting

### `Connection refused` with Ollama

```bash
ollama serve
curl http://localhost:11434/api/tags
```

### `401 Unauthorized`

Check your provider API key or Maven Central publishing token.

### `publish` fails

Check:

- namespace ownership for `in.simpletools`
- Central Portal token username and password
- GPG public key availability on supported keyservers
- required POM metadata
- Java 21 availability

## License

MIT. See [LICENSE](LICENSE).
