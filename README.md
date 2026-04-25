# LLM Client Framework for Java

> A unified Java library for interacting with **12+ LLM providers** through a single, consistent API.

[![Maven Central](https://img.shields.io/badge/Maven-v1.0.2-green)](https://search.maven.org/artifact/in.simpletools/llm-client-framework)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

## Supported Providers

| Category | Providers |
|----------|-----------|
| **Local (Free)** | Ollama, LM Studio, vLLM, Jan |
| **Cloud (API Key)** | OpenAI, Claude, DeepSeek, NVIDIA NIM, Groq, Mistral, OpenRouter |

## Related Tools

### Project Generator
Create Next.js projects instantly with custom packages.

[**Web UI**](https://simpletoolsindia.github.io/project-generator) | [**CLI**](#cli)
```bash
npx @simpletoolsindia/project-generator my-app
npx @simpletoolsindia/project-generator my-app --packages prisma,next-auth
```

---

## Quick Start

### 1. Add Dependency

**Gradle** (`build.gradle`):
```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.2'
}
```

**Maven** (`pom.xml`):
```xml
<dependency>
    <groupId>in.simpletools</groupId>
    <artifactId>llm-client-framework</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 2. Five-Minute Example

```java
import in.simpletools.llm.framework.client.*;

public class Main {
    public static void main(String[] args) {
        // Use free local Ollama
        LLMClient client = LLMClient.ollama("gemma4:latest");

        // Simple chat
        String reply = client.chat("What is recursion?");
        System.out.println(reply);
    }
}
```

### 3. Run

```bash
./gradlew run
```

---

## Key Features

### One API, All Providers

```java
// Local (free, no API key)
LLMClient ollama = LLMClient.ollama("gemma4:latest");

// Cloud providers
LLMClient openai = LLMClient.openAI("gpt-4o-mini", "sk-...");
LLMClient claude = LLMClient.claude("claude-3-5-sonnet-20241022", "sk-ant-...");
LLMClient deepseek = LLMClient.deepSeek("deepseek-chat", "sk-...");
LLMClient groq = LLMClient.groq("llama-3.1-70b-versatile", "gsk-...");
```

### Fluent Tool Registration

```java
LLMClient client = LLMClient.ollama("gemma4:latest");

// Simple tools with lambdas
client.tool("calculate", "Evaluate math expression",
    args -> new javax.script.ScriptEngineManager()
        .getEngineByName("JavaScript").eval(args.get("expression"))
);

// No-param tools
client.tool("get_date", "Get current date",
    () -> new java.util.Date().toString());

// Ask with tool usage
String reply = client.chat("What is 25 * 4 + 10? Use the calculate tool.");
```

### Annotation-Based Auto-Registration

```java
class MyTools {
    @LLMTool(name = "calculate", description = "Evaluate math expression")
    public double calculate(@ToolParam("expression") String expr) {
        return (double) new javax.script.ScriptEngineManager()
            .getEngineByName("JavaScript").eval(expr);
    }

    @LLMTool(name = "get_weather", description = "Get weather for a city")
    public String weather(@ToolParam("city") String city) {
        return "Weather in " + city + ": Sunny, 72°F";
    }
}

// Register all at once
LLMClient client = LLMClient.ollama("gemma4:latest");
client.registerTools(new MyTools());
```

### Built-in System Tools (File, Web, Shell)

```java
LLMClient client = LLMClient.ollama("gemma4:latest");

// Enable all system tools
client.withSystemTools();

// Now the LLM can:
// - read_file, write_file, list_dir, find_files, grep
// - web_search, fetch_webpage
// - run_bash

String reply = client.chat(
    "Read /tmp/notes.txt, then search the web for Java 21 news"
);
```

### HTTP Client Tools (Call External APIs)

```java
client.withHttpTools();

// http_get, http_post, http_put, http_patch, http_delete

String reply = client.chat(
    "GET https://jsonplaceholder.typicode.com/users/1 and tell me the name"
);
```

### Async & Streaming

```java
// Non-blocking async
CompletableFuture<String> future = client.chatAsync("Write a story");

// Streaming tokens
client.streamChat("Count to 5",
    token -> System.out.print(token),      // on token
    error -> System.err.println(error)     // on error
);

// Parallel requests
CompletableFuture.allOf(
    client.chatAsync("What's 2+2?"),
    client.chatAsync("What's 3+3?"),
    client.chatAsync("What's 4+4?")
).join();
```

### Persistent Conversation with Redis

```java
LLMClient client = LLMClient.openAI("gpt-4o-mini", "sk-...");

// Use Redis for persistent history
client.withRedisHistory("user-123-session");

client.chat("My name is Alice");
client.chat("What's my name?");  // Gets "Alice"

client.withRedisHistory("user-123-session");  // Resume session
client.chat("What did I tell you?");  // Remembers "Alice"
```

### Token Tracking

```java
// Check context usage
var info = client.getContextInfo();
System.out.println("Used: " + info.usedTokens() + " / " + info.totalLimit());
System.out.println("Remaining: " + info.remainingTokens());
System.out.println("Usage: " + String.format("%.1f%%", info.usagePercent()));

// Auto clear when near limit
if (client.getTokenTracker().isNearLimit()) {
    client.clearHistory();
}
```

---

## Complete Examples

### Calculator with Tool Calling

```java
import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.tool.*;

public class Calculator {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest");

        client.tool("calculate", "Evaluate a mathematical expression",
            args -> {
                String expr = args.get("expression").toString();
                try {
                    return new javax.script.ScriptEngineManager()
                        .getEngineByName("JavaScript").eval(expr);
                } catch (Exception e) { return "Error: " + e.getMessage(); }
            },
            Map.of("expression", new ToolRegistry.ParamInfo(
                "expression", "The math expression to evaluate", true, String.class)));

        String reply = client.chat(
            "Calculate (125 + 375) / 25. Use the calculate tool."
        );
        System.out.println(reply);
    }
}
```

### Multi-Tool Chain

```java
LLMClient client = LLMClient.openAI("gpt-4o-mini", "sk-...");

client.withSystemTools();
client.withHttpTools();

String reply = client.chat(
    "Find all .java files in /project, read the largest one, " +
    "then POST its summary to https://httpbin.org/post"
);
```

### Builder Pattern

```java
LLMClient client = LLMClient.builder()
    .config(ClientConfig.of(Provider.OPENAI).model("gpt-4o-mini").apiKey("sk-..."))
    .history(new ConversationHistory(50))  // keep last 50 messages
    .retry(new LLMClient.RetryConfig(5,
        java.time.Duration.ofMillis(500), 2.0,
        java.time.Duration.ofSeconds(30)))
    .build();
```

### Full-Featured Setup

```java
LLMClient client = LLMClient.openAI("gpt-4o", "sk-...")
    .withRedisHistory("my-session")
    .withSystemTools()
    .withHttpTools()
    .setLogLevel(SimpleLogger.Level.INFO);

client.tool("calculate", "Evaluate math",
    args -> eval(args.get("expression").toString()));

String reply = client.chat("Calculate 100*100 and search for Java news");
client.printContextInfo();
```

---

## API Reference

### Factory Methods

| Provider | Code |
|----------|------|
| Ollama | `LLMClient.ollama("model")` |
| LM Studio | `LLMClient.lmStudio("model")` |
| vLLM | `LLMClient.vllm("model")` |
| Jan | `LLMClient.jan("model")` |
| OpenAI | `LLMClient.openAI("gpt-4o", "sk-...")` |
| Claude | `LLMClient.claude("claude-3-5-sonnet", "sk-ant-...")` |
| DeepSeek | `LLMClient.deepSeek("deepseek-chat", "sk-...")` |
| NVIDIA | `LLMClient.nvidia("meta/llama-3.1-70b", "nv-...")` |
| Groq | `LLMClient.groq("llama-3.1-70b", "gsk-...")` |
| Mistral | `LLMClient.mistral("mistral-large", "key")` |
| OpenRouter | `LLMClient.openRouter("model", "sk-or-...")` |

### Core Methods

| Method | Description |
|--------|-------------|
| `chat(String)` | Send message, blocking |
| `chatAsync(String)` | Non-blocking, returns CompletableFuture |
| `streamChat(String, Consumer)` | Stream tokens to consumer |
| `tool(name, desc, handler)` | Register tool with lambda |
| `registerTools(Object)` | Auto-register all @LLMTool methods |
| `withSystemTools()` | Enable built-in file/web/bash tools |
| `withHttpTools()` | Enable HTTP client tools |
| `withRedisHistory(id)` | Use Redis for persistent history |
| `withMemoryHistory()` | Use in-memory history (default) |
| `getContextInfo()` | Get token usage stats |
| `clearHistory()` | Clear conversation history |
| `setLogLevel(Level)` | Set logging level (DEBUG, INFO, WARN, ERROR) |

### @LLMTool Annotation

```java
@LLMTool(
    name = "tool_name",
    description = "What it does",
    maxRetries = 3,              // Retry attempts (0 = disabled)
    retryDelayMs = 500,          // Initial delay ms
    backoffMultiplier = 2.0,     // Backoff multiplier
    maxRetryDelayMs = 10000      // Max delay ms
)
```

### @ToolParam Annotation

```java
@ToolParam(
    name = "param_name",
    description = "What it is",
    required = false             // Defaults to true
)
```

---

## Built-in System Tools

### File Operations

| Tool | Args | Description |
|------|------|-------------|
| `read_file` | `path` | Read file contents |
| `write_file` | `path`, `content` | Write/overwrite file |
| `create_file` | `path` | Create empty file |
| `append_file` | `path`, `content` | Append to file |
| `delete_file` | `path` | Delete file/directory |
| `list_dir` | `path`, `recursive` | List directory |
| `find_files` | `path`, `pattern` | Find by glob pattern |
| `grep` | `path`, `pattern`, `file_pattern` | Search text in files |
| `path_exists` | `path` | Check if path exists |
| `file_info` | `path` | Get file metadata |

### Web Tools

| Tool | Args | Description |
|------|------|-------------|
| `web_search` | `query`, `limit` | Search the web |
| `fetch_webpage` | `url`, `max_length` | Fetch webpage content |

### Shell

| Tool | Args | Description |
|------|------|-------------|
| `run_bash` | `command`, `cwd` | Execute shell command |

### HTTP Client

| Tool | Args | Description |
|------|------|-------------|
| `http_get` | `url`, `headers`, `params` | GET request |
| `http_post` | `url`, `headers`, `body` | POST request |
| `http_put` | `url`, `headers`, `body` | PUT request |
| `http_patch` | `url`, `headers`, `body` | PATCH request |
| `http_delete` | `url`, `headers` | DELETE request |

---

## Project Structure

```
src/main/java/in/simpletools/llm/framework/
├── client/
│   ├── LLMClient.java            # Main client with fluent API
│   └── LLMClientFactory.java     # Static factory methods
├── config/
│   ├── Provider.java             # 12+ provider enum
│   └── ClientConfig.java        # Configuration
├── adapter/
│   ├── ProviderAdapter.java      # Adapter interface
│   ├── OllamaAdapter.java        # Ollama adapter
│   ├── OpenAIAdapter.java        # OpenAI-compatible adapter
│   └── ClaudeAdapter.java        # Anthropic Claude adapter
├── model/
│   ├── Message.java, LLMRequest.java, LLMResponse.java
│   └── Tool.java, ToolCall.java
├── tool/
│   ├── LLMTool.java              # Tool annotation
│   ├── ToolParam.java            # Parameter annotation
│   ├── ToolRegistry.java         # Auto-discovery + retry
│   └── OllamaTool.java           # Legacy annotation
├── history/
│   ├── ConversationHistory.java   # In-memory history
│   ├── RedisHistory.java         # Redis-backed history
│   └── TokenTracker.java         # Token usage tracking
├── tools/
│   ├── SystemTools.java          # Built-in file/web/bash tools
│   └── HttpTools.java            # HTTP client tools
└── utils/
    └── SimpleLogger.java          # Built-in logger
```

---

## Setup

### Local: Install Ollama

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull a model
ollama pull gemma4:latest    # ~4GB
ollama pull llama3.2:3b       # ~2GB

# Start server
ollama serve

# Test
curl http://localhost:11434/api/tags
```

### Cloud: Get API Keys

- **OpenAI**: https://platform.openai.com/api-keys
- **Claude**: https://console.anthropic.com/settings/keys
- **DeepSeek**: https://platform.deepseek.com/api_keys

### CLI

```bash
# Install globally
npm install -g @simpletoolsindia/project-generator

# Create a Next.js project
project-generator my-app

# With packages
project-generator my-app --packages prisma,next-auth,tailwindcss

# Interactive mode
project-generator
```

---

## Troubleshooting

### Connection refused (Local Providers)

```bash
# Check Ollama is running
ollama serve

# Test manually
curl http://localhost:11434/api/tags
```

### 401 Unauthorized (Cloud)

```bash
# Verify key is set
echo $OPENAI_API_KEY

# Test the key
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

### Tool not called

1. Tool description must be clear
2. Parameter names must match LLM expectations
3. Some models don't support tools (use OpenAI or Claude)

### Out of context

```java
// Check usage
client.printContextInfo();

// Clear when near limit
if (client.getTokenTracker().isNearLimit()) {
    client.clearHistory();
}
```

---

## Architecture

```
┌─────────────────────────────────────────┐
│              Your Application            │
└─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│                 LLMClient               │
│  .tool() / .registerTools()            │
│  .chat() / .chatAsync() / .streamChat() │
│  .withSystemTools() / .withHttpTools()  │
└─────────────────────────────────────────┘
                    │
          ┌────────┴────────┐
          ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│   ToolRegistry   │  │    History       │
│  @LLMTool auto   │  │  In-memory/Redis  │
│  Retry + Backoff │  │  Token Tracking  │
└──────────────────┘  └──────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────┐
│             ProviderAdapter             │
│           (Strategy Pattern)            │
│  ┌─────────┬─────────┬─────────┐        │
│  │ Ollama  │ OpenAI  │ Claude  │  ...   │
│  └─────────┴─────────┴─────────┘        │
└─────────────────────────────────────────┘
```

---

## License

MIT License — See [LICENSE](LICENSE)