# LLM Client Framework for Java

Unified Java 21 client for local and cloud LLM providers with chat, streaming, tool calling, conversation history, context tracking, auto-compaction, and built-in tools.

[![Java](https://img.shields.io/badge/Java-21+-1f6feb)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

## What You Can Build

- Local AI apps with Ollama, LM Studio, vLLM, or Jan.
- Cloud AI apps with OpenAI, Claude, DeepSeek, NVIDIA, OpenRouter, Groq, or Mistral.
- CLI tools, Spring services, desktop apps, batch jobs, and agent-like workflows.
- LLM flows that call your Java methods as tools.
- Long-running chats with history, token tracking, and automatic compaction.
- Apps that need built-in file, shell, web search, webpage fetch, or HTTP tools.

## Features

- One `LLMClient` API for local and cloud providers.
- Blocking chat with `chat(...)`.
- Async chat with `chatAsync(...)`.
- Real streaming callbacks with `streamChat(...)`.
- Lambda tool registration.
- Annotation-driven tool registration with `@LLMTool` and `@ToolParam`.
- Built-in system tools: file read/write/create/append/delete, directory listing, file search, grep, metadata, web search, webpage fetch, shell commands.
- Built-in HTTP tools: GET, POST, PUT, PATCH, DELETE.
- Configurable web search provider: DuckDuckGo HTML or SearXNG JSON.
- In-memory conversation history.
- Redis-backed history with fallback.
- Context-window tracking.
- Automatic context compaction with rolling summaries.
- Manual compaction.
- Verbose developer logging.
- Provider-neutral request/response/message/tool models.
- Javadocs designed for IDE autocomplete and generated API docs.

## Install

### Gradle

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.9'
}
```

### Maven

```xml
<dependency>
    <groupId>in.simpletools</groupId>
    <artifactId>llm-client-framework</artifactId>
    <version>1.0.9</version>
</dependency>
```

## Quick Start With Ollama

Start Ollama and pull a model:

```bash
ollama serve
ollama pull gemma4:latest
```

Create a client:

```java
import in.simpletools.llm.framework.client.LLMClient;

public class Main {
    public static void main(String[] args) {
        try (LLMClient client = LLMClient.ollama("gemma4:latest")) {
            String reply = client.chat("Explain Java records in simple words.");
            System.out.println(reply);
        }
    }
}
```

## Provider Examples

### Ollama

```java
LLMClient client = LLMClient.ollama("gemma4:latest");
```

Custom Ollama URL:

```java
LLMClient client = LLMClient.ollama("http://localhost:11434", "gemma4:latest");
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

### OpenRouter

```java
LLMClient client = LLMClient.openRouter(
    "openai/gpt-4o-mini",
    System.getenv("OPENROUTER_API_KEY")
);
```

### Builder API

Use the builder when you want custom config, history, adapter, executor, logger, or token tracker.

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
            .temperature(0.2)
            .timeoutSeconds(60)
    )
    .history(new ConversationHistory(100))
    .build();
```

## Chat

### Basic Chat

```java
String reply = client.chat("Write a short welcome message.");
System.out.println(reply);
```

### Chat With Per-Request Options

Supported options include:

- `system`: temporary system prompt for this request.
- `temperature`: sampling temperature as a string.

```java
import java.util.Map;

String reply = client.chat(
    "Draft a short release note.",
    Map.of(
        "system", "You are a concise technical writer.",
        "temperature", "0.2"
    )
);
```

## Streaming

`streamChat(...)` calls your callback as chunks arrive from the provider and returns after streaming completes.

```java
client.streamChat(
    "Count from 1 to 5.",
    chunk -> System.out.print(chunk),
    error -> System.err.println("Stream error: " + error)
);
```

Simple overload:

```java
client.streamChat("Tell me a short story.", System.out::print);
```

Important notes:

- The call is blocking until the stream completes.
- The callback is invoked for each text chunk.
- Conversation history is updated with the full streamed assistant response.
- For OpenAI-compatible providers and Claude, SSE `data:` lines are parsed as they arrive.
- For Ollama, JSON lines from `/api/chat` are parsed as they arrive.
- When tools are registered, `streamChat(...)` runs the tool-capable chat flow and prints the final tool-assisted reply.

## Live Status Events

Use live status events when building IDE agents, terminal assistants, or Claude Code style applications. Events tell your UI what the framework is doing while the request is still running.

```java
import in.simpletools.llm.framework.client.LLMStatus;

client.onStatus(status -> {
    switch (status.type()) {
        case CHAT_STARTED -> System.out.println("Thinking...");
        case TOOL_CALL_REQUESTED -> System.out.println("Tool requested: " + status.toolName());
        case TOOL_EXECUTION_STARTED -> System.out.println("Running: " + status.toolName());
        case TOOL_RESPONSE_VALIDATED -> System.out.println("Tool result validated");
        case CONTINUATION_STARTED -> System.out.println("Continuing with tool result...");
        case STREAM_CHUNK -> System.out.print(status.result());
        case CHAT_COMPLETED -> System.out.println("Done");
        case ERROR -> System.err.println(status.message());
        default -> { }
    }
});

String reply = client.chat("Use city_tip for Jaipur in winter.");
```

Per streaming call:

```java
client.streamChatWithStatus(
    "Use city_tip for Jaipur in winter.",
    System.out::print,
    status -> System.err.println(status.type() + " " + status.toolName())
);
```

Real weather API streaming example:

```bash
./gradlew run --args="Jaipur"
```

Or run the demo class directly:

```java
import in.simpletools.llm.framework.example.WeatherToolStreamingDemo;

WeatherToolStreamingDemo.main(new String[] {"Jaipur"});
```

The demo registers a `get_weather` tool, calls Open-Meteo for real weather data, prints live tool status with elapsed milliseconds, and streams the final answer:

```text
[ 120 ms] thinking
[1020 ms] tool requested: get_weather {city=Jaipur}
[1025 ms] running tool: get_weather
[1850 ms] tool result validated: get_weather
[1860 ms] continuing with tool result
```

Status types include:

- `CHAT_STARTED`
- `REQUEST_SENT`
- `RESPONSE_RECEIVED`
- `STREAM_STARTED`
- `STREAM_CHUNK`
- `STREAM_COMPLETED`
- `TOOL_CALL_REQUESTED`
- `TOOL_EXECUTION_STARTED`
- `TOOL_EXECUTION_COMPLETED`
- `TOOL_EXECUTION_FAILED`
- `TOOL_RESPONSE_VALIDATED`
- `TOOL_RESPONSE_APPENDED`
- `CONTINUATION_STARTED`
- `CHAT_COMPLETED`
- `ERROR`

## Async Chat

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<String> future = client.chatAsync("Summarize virtual threads.");
String reply = future.join();
```

With options:

```java
CompletableFuture<String> future = client.chatAsync(
    "Write a title.",
    Map.of("temperature", "0.1")
);
```

## Tool Calling

Tool calling lets the model ask your Java code for data or actions.

### Lambda Tool

```java
import in.simpletools.llm.framework.tool.ToolRegistry;
import java.util.Map;

client.tool(
    "calculate",
    "Evaluate a simple math expression",
    args -> {
        String expression = args.get("expression").toString();
        return expression + " = 42";
    },
    Map.of(
        "expression",
        new ToolRegistry.ParamInfo(
            "expression",
            "Math expression to evaluate",
            true,
            String.class
        )
    )
);

String reply = client.chat("Use calculate for 25 * 4 + 10.");
```

### Annotation Tool

```java
import in.simpletools.llm.framework.tool.LLMTool;
import in.simpletools.llm.framework.tool.ToolParam;

public class TravelTools {
    @LLMTool(name = "city_tip", description = "Return a short travel tip for a city")
    public String cityTip(
        @ToolParam(name = "city", description = "City name") String city,
        @ToolParam(name = "season", description = "Travel season", required = false) String season
    ) {
        return "Travel tip for " + city + ": start early and pre-book major attractions.";
    }
}
```

Register and use it:

```java
client.registerTools(new TravelTools());

String reply = client.chat("""
    Use the city_tip tool for city=Jaipur and season=winter.
    Keep the answer concise.
    """);
```

### Structured Tool Results

Tool outputs are wrapped in a consistent JSON envelope before they are sent back to the model. This helps the model produce clearer user-facing answers and helps apps inspect tool behavior.

Successful tool result:

```json
{
  "tool": "city_tip",
  "ok": true,
  "status": "success",
  "arguments": {
    "city": "Jaipur",
    "season": "winter"
  },
  "result": "Travel tip for Jaipur in winter: start early.",
  "message": "Tool completed and returned usable data."
}
```

If a tool throws an exception, is missing, returns empty text, or returns failure-like text such as `sorry`, `unable`, `not available`, or `error`, the framework marks the result as failed:

```json
{
  "tool": "city_tip",
  "ok": false,
  "status": "failed",
  "arguments": {
    "city": "Jaipur",
    "season": "winter"
  },
  "result": "Sry I'm not able to answer right now!",
  "message": "Tool returned an unavailable or failure-like response instead of useful data.",
  "user_message": "The city_tip tool could not return a usable result. Explain this clearly to the user instead of giving a vague answer."
}
```

`TOOL_RESPONSE_VALIDATED` status events include this same structured payload.

### Retry Tool Calls

```java
client.withRetry(5);
```

Per-tool retry using annotation:

```java
@LLMTool(
    name = "fetch_order",
    description = "Fetch order details",
    maxRetries = 3,
    retryDelayMs = 500,
    backoffMultiplier = 2.0
)
public String fetchOrder(@ToolParam(name = "order_id") String orderId) {
    return "Order " + orderId + " is shipped.";
}
```

## Built-In System Tools

Register all system tools:

```java
client.withSystemTools();
```

Register only one category:

```java
client.withSystemTools("file");  // file, directory, find, grep, metadata
client.withSystemTools("web");   // web_search, fetch_webpage
client.withSystemTools("shell"); // run_bash
```

Available tools:

| Tool | What it does |
|---|---|
| `read_file` | Read a text file |
| `write_file` | Write or overwrite a file |
| `create_file` | Create or touch a file |
| `append_file` | Append text to a file |
| `delete_file` | Delete a file or empty directory |
| `list_dir` | List directory contents |
| `find_files` | Find files by glob |
| `grep` | Search text in files |
| `path_exists` | Check whether a path exists |
| `file_info` | Return file metadata |
| `web_search` | Search the web |
| `fetch_webpage` | Fetch and strip webpage text |
| `run_bash` | Run a shell command |

Security note: only enable file and shell tools for trusted prompts.

## Web Search Configuration

DuckDuckGo HTML search is the default:

```java
import in.simpletools.llm.framework.tools.SystemTools;

SystemTools.useDuckDuckGoSearch();
```

Use a custom DuckDuckGo-compatible URL template:

```java
SystemTools.useDuckDuckGoSearch("https://html.duckduckgo.com/html/?q=%s");
```

Use SearXNG:

```java
SystemTools.useSearxngSearch("https://search.example.com");
```

System properties are supported:

```bash
-Dsimpletools.webSearchProvider=searxng
-Dsimpletools.searxngBaseUrl=https://search.example.com
```

Example:

```java
client.withSystemTools("web");
String reply = client.chat("Search the web for Java 21 virtual threads and summarize 3 results.");
```

## Built-In HTTP Tools

Register HTTP tools:

```java
client.withHttpTools();
```

Available tools:

| Tool | What it does |
|---|---|
| `http_get` | GET a URL with optional headers and query params |
| `http_post` | POST a body |
| `http_put` | PUT a body |
| `http_patch` | PATCH a body |
| `http_delete` | DELETE a resource |

Example:

```java
client.withHttpTools();

String reply = client.chat("""
    Use http_get to call https://api.github.com/repos/simpletoolsindia/llm-client-framework.
    Summarize the repository metadata.
    """);
```

## Conversation History

History is enabled by default in memory.

```java
client.chat("My name is Priya.");
client.chat("What is my name?");
```

Inspect history:

```java
client.getHistory().getMessages().forEach(System.out::println);
```

Clear history:

```java
client.clearHistory();
```

Remove latest turns:

```java
client.clearLastN(2);
```

Use a custom in-memory limit:

```java
import in.simpletools.llm.framework.history.ConversationHistory;

LLMClient client = LLMClient.builder()
    .config(ClientConfig.of(Provider.OLLAMA).model("gemma4:latest"))
    .history(new ConversationHistory(50))
    .build();
```

## Redis History

Use Redis-backed history:

```java
LLMClient client = LLMClient.openAI("gpt-4o-mini", System.getenv("OPENAI_API_KEY"))
    .withRedisHistory("user-123");
```

With host and port:

```java
client.withRedisHistory("user-123", "localhost", 6379);
```

If Redis is unavailable, the framework falls back to in-memory history.

## Context Tracking

Print current context usage:

```java
System.out.println(client.getContextInfo().summary());
```

Read individual fields:

```java
var info = client.getContextInfo();
System.out.println(info.usedTokens());
System.out.println(info.remainingTokens());
System.out.println(info.usagePercent());
```

Project usage before sending the next message:

```java
var projected = client.getProjectedContextInfo("Summarize everything so far.");
System.out.println(projected.summary());
```

Set a manual context window:

```java
client.withContextWindow(128000);
```

## Automatic Context Compaction

Auto-compaction keeps long-running chats from overflowing the model context window.

Enable defaults:

```java
client.withAutoCompaction();
```

Tune thresholds:

```java
client.withAutoCompaction(85.0, 55.0, 6);
```

Meaning:

- Start compaction when estimated usage reaches `85%`.
- Compact toward about `55%`.
- Keep the latest `6` non-system messages in full.

Inspect the rolling summary:

```java
String summary = client.getCompactedContextSummary();
```

Compact manually:

```java
client.compactHistoryNow();
```

Disable compaction:

```java
client.withoutAutoCompaction();
```

## Verbose Logging

```java
import in.simpletools.llm.framework.utils.SimpleLogger;

client.withVerboseLogging(SimpleLogger.Level.DEBUG);
```

Disable verbose mode:

```java
client.withoutVerboseLogging();
```

Set log level only:

```java
client.setLogLevel(SimpleLogger.Level.WARN);
```

Verbose logs include:

- request message count
- tool count
- response content length
- token usage sync details
- compaction details
- thread names

## Provider-Neutral Model API

Advanced users can build requests manually:

```java
import in.simpletools.llm.framework.model.LLMRequest;
import in.simpletools.llm.framework.model.Message;

LLMRequest request = LLMRequest.builder()
    .model("gemma4:latest")
    .system("You are concise.")
    .user("Explain the framework.")
    .temperature(0.2)
    .build();
```

Build multimodal messages:

```java
Message message = Message.user()
    .text("Describe this image.")
    .image("https://example.com/image.png")
    .build();
```

## Spring Boot Example

```java
import in.simpletools.llm.framework.client.LLMClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Bean(destroyMethod = "close")
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

    public String summarize(String text) {
        return llmClient.chat("Summarize this text:\n\n" + text);
    }

    public void streamSummary(String text) {
        llmClient.streamChat(
            "Summarize this text:\n\n" + text,
            System.out::print
        );
    }
}
```

## Complete CLI Example

```java
package demo;

import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.tool.LLMTool;
import in.simpletools.llm.framework.tool.ToolParam;

public class Main {
    public static void main(String[] args) {
        try (LLMClient client = LLMClient.ollama("gemma4:latest")
                .withAutoCompaction()
                .withSystemTools("web")) {

            client.registerTools(new TravelTools());

            client.streamChat(
                "Use city_tip for Jaipur in winter, then give a concise answer.",
                System.out::print,
                error -> System.err.println("Error: " + error)
            );
        }
    }

    public static final class TravelTools {
        @LLMTool(name = "city_tip", description = "Return a city-specific travel tip")
        public String cityTip(
            @ToolParam(name = "city", description = "City name") String city,
            @ToolParam(name = "season", description = "Travel season") String season
        ) {
            return "Travel tip for " + city + " in " + season
                + ": start early and pre-book major attractions.";
        }
    }
}
```

## Examples In This Repository

- `src/main/java/in/simpletools/llm/framework/example/OllamaDemo.java`
- `examples/auto-compaction-demo`

## Project Structure

```text
src/main/java/in/simpletools/llm/framework/
├── adapter/   Provider adapters
├── client/    LLMClient and factories
├── config/    ClientConfig and Provider
├── history/   Conversation and token tracking
├── model/     Request, response, message, tool models
├── tool/      Tool annotations and registry
├── tools/     Built-in system and HTTP tools
└── utils/     Retry and logging helpers
```

## Troubleshooting

### Ollama connection refused

```bash
ollama serve
curl http://localhost:11434/api/tags
```

### Streaming prints nothing

Check that:

- you are using `1.0.9` or newer
- the model/provider supports streaming
- your program does not exit before `streamChat(...)` returns
- your callback flushes output if needed

```java
client.streamChat("Hello", chunk -> {
    System.out.print(chunk);
    System.out.flush();
});
```

### 401 Unauthorized

Check the provider API key:

```java
System.getenv("OPENAI_API_KEY");
System.getenv("ANTHROPIC_API_KEY");
```

### SearXNG search returns no results

Verify your instance supports JSON output:

```bash
curl 'https://search.example.com/search?q=java&format=json'
```

## License

MIT. See [LICENSE](LICENSE).
