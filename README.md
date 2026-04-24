# LLM Client Framework for Java

A high-performance, unified Java framework for interacting with **12+ LLM providers** through a single, consistent API. Built with modern Java patterns: CompletableFuture for async, thread-safe tool execution with retry, token tracking, and Redis-backed conversation history.

[![Maven Central](https://img.shields.io/badge/Maven-JitPack-green)](https://jitpack.io/#simpletoolsindia/llm-client-framework)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

---

## Features

- **12+ Providers** — Local (Ollama, LM Studio, vLLM, Jan) and Cloud (OpenAI, Claude, DeepSeek, Groq, Mistral, NVIDIA NIM, OpenRouter)
- **Simple Tool API** — Fluent `.tool()` registration with lambda support
- **Annotation-based Auto-Registration** — Mark methods with `@LLMTool`, auto-discovered
- **Tool Retry with Backoff** — Built-in retry with exponential backoff, configurable per-tool
- **Token Tracking** — Know your context usage at any time with `client.getContextInfo()`
- **Redis History** — Persistent multi-session conversations with Redis backend
- **Built-in System Tools** — File, web search, bash commands — cross-platform (Win/Mac/Linux)
- **Async/Await** — CompletableFuture-based async chat for high performance
- **Streaming** — Real-time token-by-token responses
- **Logging** — Built-in `SimpleLogger` (no external deps)
- **Zero Extra Dependencies** — Uses only Java standard library + Gson

---

## Installation

### JitPack (Recommended — Free)

```groovy
// build.gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.simpletoolsindia:llm-client-framework:v1.1.0'
}
```

---

## Quick Start

### Simple Chat

```java
import in.simpletools.llm.framework.client.*;

LLMClient client = LLMClient.ollama("gemma4:latest");
String reply = client.chat("What is recursion?");
System.out.println(reply);
```

### Cloud Providers

```java
// OpenAI
LLMClient openai = LLMClient.openAI("gpt-4o-mini", "sk-...");

// Claude
LLMClient claude = LLMClient.claude("claude-3-5-sonnet", "sk-ant-...");

// DeepSeek
LLMClient deepseek = LLMClient.deepSeek("deepseek-chat", "sk-...");
```

---

## Tool Registration — Easy Fluent API

Tools are now dead simple. Use `.tool()` with a lambda:

```java
LLMClient client = LLMClient.ollama("gemma4:latest");

// Simple tool — just a name, description, and lambda
client.tool("ping", "Returns pong", () -> "pong");

// Tool with parameters
client.tool("calculate", "Evaluate a math expression",
    args -> {
        String expr = args.get("expression").toString();
        return new javax.script.ScriptEngineManager()
            .getEngineByName("JavaScript").eval(expr);
    },
    Map.of("expression", new ToolRegistry.ParamInfo(
        "expression", "Math expression", true, String.class)));

// Tool with custom retry (5 attempts, 1s delay)
client.tool("risky_api", "Call external API",
    args -> callApi(args),
    Map.of(), 5, 1000, 2.0, 30000);
```

### Annotation-Based Auto-Registration

Annotate methods with `@LLMTool` and register the whole object at once:

```java
public class MyTools {
    @LLMTool(name = "calculate", description = "Evaluate math", maxRetries = 3)
    public double calculate(@ToolParam("expr") String expr) {
        return eval(expr);
    }

    @LLMTool(name = "web_search", description = "Search the web")
    public String search(@ToolParam("query") String query) {
        return doSearch(query);
    }
}

// Register all tools at once — auto-discovers @LLMTool methods
LLMClient client = LLMClient.ollama("gemma4:latest");
client.registerTools(new MyTools());
```

### Built-in System Tools

```java
LLMClient client = LLMClient.ollama("gemma4:latest");

// Register ALL system tools (file, web, bash)
client.withSystemTools();

// Or selectively
client.withSystemTools("file");   // read_file, write_file, create_file, list_dir, grep, etc.
client.withSystemTools("web");     // web_search, fetch_webpage
client.withSystemTools("shell");   // run_bash

// Now the LLM can use these tools automatically
String reply = client.chat(
    "Read README.md and tell me what it's about"
);
```

Available system tools:

| Tool | Description | Platforms |
|------|-------------|-----------|
| `read_file` | Read file contents | Win/Mac/Linux |
| `write_file` | Write/overwrite a file | Win/Mac/Linux |
| `create_file` | Create empty file or touch | Win/Mac/Linux |
| `append_file` | Append to a file | Win/Mac/Linux |
| `delete_file` | Delete a file | Win/Mac/Linux |
| `list_dir` | List directory contents | Win/Mac/Linux |
| `find_files` | Find files by glob pattern | Win/Mac/Linux |
| `grep` | Search text within files | Win/Mac/Linux |
| `path_exists` | Check if path exists | Win/Mac/Linux |
| `file_info` | Get file metadata | Win/Mac/Linux |
| `web_search` | Perform a web search | Win/Mac/Linux |
| `fetch_webpage` | Fetch webpage content | Win/Mac/Linux |
| `run_bash` | Execute shell command | Win/Mac/Linux |

---

## Token Tracking & Context Window

Know your context usage at any time:

```java
LLMClient client = LLMClient.openAI("gpt-4o", "sk-...");

// After chatting
client.chat("Tell me about Java");
client.chat("What is CompletableFuture?");

// Get context info
TokenTracker.ContextInfo info = client.getContextInfo();
System.out.println(info.summary());
// Output: [gpt-4o] 2847 / 128000 tokens (2.2%) | prompt=1234, completion=1613 | 4 messages

// Check if running low
if (client.getTokenTracker().isNearLimit()) {
    client.clearHistory();
}

// Print detailed breakdown
client.printContextInfo();
```

Known model limits are auto-configured. You can also set manually:

```java
client.getTokenTracker().setModel("my-model", 32000L);
```

---

## Redis-Backed Conversation History

Persistent conversations across sessions:

```java
LLMClient client = LLMClient.ollama("gemma4:latest");

// Attach Redis-backed history
client.withRedisHistory("user-123-conversation");

// All chat history is now persisted to Redis
client.chat("My name is Ravi");
client.chat("What city do I live in?");

// Save/load conversations
// (also auto-saves on each message with 24h TTL)

// Switch to a different conversation
client.withRedisHistory("user-456-different-topic");

// Back to in-memory (default)
client.withMemoryHistory();
```

For Redis unavailable scenarios, an in-memory fallback is used automatically.

---

## Logging

Built-in logger with no external dependencies:

```java
LLMClient client = LLMClient.ollama("gemma4:latest")
    .setLogLevel(SimpleLogger.Level.DEBUG);

// Or configure globally
SimpleLogger.setGlobalLevel(SimpleLogger.Level.INFO);
SimpleLogger.get("LLMClient").info("Starting...");
SimpleLogger.get("LLMClient").debug("Request: {}", request);
SimpleLogger.get("LLMClient").warn("Retrying after failure");
SimpleLogger.get("LLMClient").error("Failed", exception);
```

---

## Async & Streaming

```java
LLMClient client = LLMClient.ollama("gemma4:latest");

// Async — non-blocking
CompletableFuture<String> future = client.chatAsync("Tell me a story");
future.thenAccept(reply -> System.out.println("Got reply!"));

// Streaming — real-time tokens
client.streamChat("Count to 10", chunk -> System.out.print(chunk));
```

---

## @LLMTool Annotation Reference

```java
@LLMTool(
    name = "my_tool",           // Tool name (defaults to method name)
    description = "Does X",    // Description (defaults to method name)
    maxRetries = 3,             // Retry attempts (default 3, 0 = disable)
    retryDelayMs = 500,         // Initial delay ms (default 500)
    backoffMultiplier = 2.0,   // Backoff multiplier (default 2.0)
    maxRetryDelayMs = 10000    // Max delay ms (default 10000)
)
```

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                      Your Application                │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│                        LLMClient                     │
│  .tool() / .registerTools() / .withSystemTools()   │
│  .chat() / .chatAsync() / .streamChat()            │
│  .getContextInfo() / .withRedisHistory()            │
│  TokenTracker + SimpleLogger (built-in)            │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│               ToolRegistry (auto-reg)                │
│  @LLMTool discovery | retry with backoff            │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│                    ProviderAdapter                   │
│              (Strategy Pattern)                     │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│   │ Ollama   │  │ OpenAI   │  │ Claude   │        │
│   └──────────┘  └──────────┘  └──────────┘        │
└─────────────────────────────────────────────────────┘
```

**Design Patterns Used:**
- **Factory Pattern** — `LLMClient.ollama()`, `LLMClient.openAI()`, etc.
- **Builder Pattern** — `LLMClient.builder()` for fluent configuration
- **Strategy Pattern** — `ProviderAdapter` for pluggable LLM backends
- **Auto-Registration** — `@LLMTool` annotation for zero-config tools
- **Retry Pattern** — Exponential backoff per-tool, configurable

---

## Project Structure

```
src/main/java/in/simpletools/llm/framework/
├── client/
│   ├── LLMClient.java           # Main client — tools, chat, history
│   └── LLMClientFactory.java    # Static factory shortcuts
├── config/
│   ├── Provider.java             # Enum for all 12+ providers
│   └── ClientConfig.java        # Configuration builder
├── model/
│   ├── Message.java             # Chat messages
│   ├── LLMRequest.java         # Request builder
│   ├── LLMResponse.java         # Response + Usage
│   ├── Tool.java               # Tool definitions
│   └── ToolCall.java           # Tool invocation
├── adapter/
│   ├── ProviderAdapter.java     # Adapter interface
│   ├── OllamaAdapter.java      # Ollama implementation
│   ├── OpenAIAdapter.java      # OpenAI-compatible
│   └── ClaudeAdapter.java      # Claude API
├── tool/
│   ├── LLMTool.java            # @LLMTool annotation (with retry)
│   ├── ToolParam.java          # @ToolParam annotation
│   ├── ToolRegistry.java       # Tool registry + auto-discovery
│   └── OllamaTool.java        # Legacy annotation (still supported)
├── history/
│   ├── ConversationHistory.java  # In-memory history
│   ├── ConversationHistoryStore.java  # History interface
│   ├── RedisHistory.java       # Redis-backed history
│   └── TokenTracker.java      # Token usage + context tracking
├── tools/
│   └── SystemTools.java        # Built-in file/web/bash tools
└── utils/
    └── SimpleLogger.java      # Built-in logger (no deps)
```

---

## Environment Setup

### Ollama (Local — Recommended)

```bash
# Install
curl -fsSL https://ollama.com/install.sh | sh

# Pull model
ollama pull gemma4:latest

# Run server (localhost:11434)
ollama serve
```

### API Keys

```bash
export OPENAI_API_KEY=sk-your-key-here
export ANTHROPIC_API_KEY=sk-ant-your-key-here
export DEEPSEEK_API_KEY=sk-your-key-here
```

---

## Build & Run

```bash
# Build
gradle build

# Run demo
gradle run

# Clean
gradle clean
```

> Note: Requires Gradle 7+ or Java 21-compatible build tooling.

---

## Troubleshooting

### Connection Refused (Local Providers)
1. Ensure Ollama/LM Studio is running: `ollama serve`
2. Check URL: custom host if not on localhost

### 401 Unauthorized (Cloud)
1. Verify API key is valid
2. Check key has required permissions

### Tool Not Being Called
1. Ensure tool description is clear
2. Check parameter types match
3. Some models don't support function calling (use OpenAI or Claude)

### Context Window Full
```java
// Check usage
client.printContextInfo();

// Clear history when near limit
client.clearHistory();

// Or check programmatically
if (client.getTokenTracker().isNearLimit()) {
    client.clearHistory();
}
```

---

## License

MIT License — See [LICENSE](LICENSE)

---

**Repository**: https://github.com/simpletoolsindia/llm-client-framework
**Issues**: https://github.com/simpletoolsindia/llm-client-framework/issues
