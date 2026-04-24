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

### Gradle (build.gradle)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.simpletoolsindia:llm-client-framework:v1.1.0'
}
```

### Maven (pom.xml)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.simpletoolsindia</groupId>
    <artifactId>llm-client-framework</artifactId>
    <version>v1.1.0</version>
</dependency>
```

### Manual Build

```bash
git clone https://github.com/simpletoolsindia/llm-client-framework.git
cd llm-client-framework
./gradlew build    # requires Gradle 7+ or Java 21
./gradlew install # install to local Maven repo
```

---

## Setup

### 1. Java Requirement

Requires **Java 21** or higher.

```bash
java --version   # should show 21+
```

### 2. Choose a Provider

#### Option A: Local (Free, No API Key)

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull a model
ollama pull gemma4:latest    # ~4GB, fast
ollama pull llama3.2:3b       # ~2GB, great for coding
ollama pull codellama:7b      # code-specialized

# Start server (runs on localhost:11434)
ollama serve
```

#### Option B: Cloud (API Key Required)

Get free API keys:
- **OpenAI**: https://platform.openai.com/api-keys
- **Claude**: https://console.anthropic.com/settings/keys
- **DeepSeek**: https://platform.deepseek.com/api_keys

```bash
# Set environment variables
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export DEEPSEEK_API_KEY=sk-...
```

### 3. Quick Test

```bash
cd llm-client-framework
./gradlew run   # runs OllamaDemo
```

---

## Quick Start

### Minimal Example

```java
import in.simpletools.llm.framework.client.*;

public class Main {
    public static void main(String[] args) {
        // Create client — local Ollama (free!)
        LLMClient client = LLMClient.ollama("gemma4:latest");

        // One-liner chat
        String reply = client.chat("What is recursion?");
        System.out.println(reply);
    }
}
```

### Cloud Providers

```java
// OpenAI
LLMClient openai = LLMClient.openAI("gpt-4o-mini", "sk-your-key");

// Anthropic Claude
LLMClient claude = LLMClient.claude("claude-3-5-sonnet-20241022", "sk-ant-your-key");

// DeepSeek
LLMClient deepseek = LLMClient.deepSeek("deepseek-chat", "sk-your-key");

// NVIDIA NIM (free tier available)
LLMClient nvidia = LLMClient.nvidia("meta/llama-3.1-70b-instruct", "nv-your-key");

// Groq (fast, free tier)
LLMClient groq = LLMClient.groq("llama-3.1-70b-versatile", "gsk-your-key");

// Mistral
LLMClient mistral = LLMClient.mistral("mistral-large-latest", "your-key");

// OpenRouter (100+ models, unified API)
LLMClient openrouter = LLMClient.openRouter("anthropic/claude-3.5-sonnet", "sk-or-your-key");
```

### Custom Endpoint

```java
// Local model with custom host
LLMClient client = LLMClient.ollama("http://192.168.1.100:11434", "llama3.2:latest");

// LM Studio
LLMClient lmstudio = LLMClient.lmStudio("qwen2.5-coder-7b");

// vLLM server
LLMClient vllm = LLMClient.vllm("mistral-7b-instruct");
```

---

## Complete Examples

### Example 1: Assistant with Tools

```java
import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.tool.*;
import java.util.concurrent.CompletableFuture;

public class Assistant {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest");

        // Register tools
        client.tool("calculate", "Evaluate a mathematical expression",
            args -> {
                String expr = args.get("expression").toString();
                return new javax.script.ScriptEngineManager()
                    .getEngineByName("JavaScript").eval(expr);
            },
            Map.of("expression", new ToolRegistry.ParamInfo(
                "expression", "The math expression", true, String.class)));

        client.tool("get_date", "Get current date", () -> new java.util.Date().toString());

        // Chat with tool usage
        String reply = client.chat(
            "What is 125 + 375? Use the calculate tool. Also tell me today's date."
        );

        System.out.println(reply);
    }
}
```

### Example 2: Persistent Conversation with Redis

```java
public class PersistentChat {
    public static void main(String[] args) {
        LLMClient client = LLMClient.openAI("gpt-4o-mini", "sk-your-key");

        // Use Redis for persistent history
        client.withRedisHistory("user-123-session");

        // Conversation 1
        client.chat("My name is Alice and I work as a developer");
        client.chat("What's my name and profession?");

        // Start fresh conversation but same user
        client.withRedisHistory("user-123-session");  // Load previous context

        // Continue from where we left off
        String reply = client.chat("What did I tell you about myself?");
        System.out.println(reply);

        // New conversation
        client.withRedisHistory("user-456-session");
        client.chat("This is a completely different user");
    }
}
```

### Example 3: Auto-Discovery with Annotations

```java
import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.tool.*;

public class AnnotatedTools {
    public static void main(String[] args) {
        // Create tool class with annotations
        MyTools tools = new MyTools();

        // Register all annotated methods at once
        LLMClient client = LLMClient.ollama("gemma4:latest");
        client.registerTools(tools);

        // Use the tools
        String reply = client.chat(
            "Calculate sqrt(144) and search for the latest Java news"
        );
        System.out.println(reply);
    }
}

// Separate tool class
class MyTools {
    @LLMTool(name = "calculate", description = "Evaluate math", maxRetries = 2)
    public double calculate(@ToolParam("expr") String expr) {
        try {
            return (double) new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript").eval(expr);
        } catch (Exception e) { return 0; }
    }

    @LLMTool(name = "web_search", description = "Search the web", maxRetries = 3)
    public String search(@ToolParam("query") String query) {
        return "Search results for: " + query;
    }

    @LLMTool(name = "get_weather", description = "Get weather for a city")
    public String weather(@ToolParam("city") String city) {
        return "Weather in " + city + ": Sunny, 72°F";
    }
}
```

### Example 4: System Tools (File & Web)

```java
public class SystemToolsExample {
    public static void main(String[] args) {
        LLMClient client = LLMClient.claude("claude-3-5-sonnet", "sk-ant-...");

        // Register all built-in system tools
        client.withSystemTools();

        // The LLM can now read files, search the web, run commands...

        // Example 1: Read a file
        String about = client.chat(
            "Read the file at /tmp/notes.txt and summarize it"
        );

        // Example 2: Web search
        String news = client.chat(
            "Search the web for the latest Java 21 features"
        );

        // Example 3: Execute command
        String disk = client.chat(
            "Run 'df -h' and tell me about disk usage"
        );

        // Example 4: Write file
        String write = client.chat(
            "Create a file /tmp/test.txt with content 'Hello from LLM!'"
        );

        // Example 5: List directory
        String files = client.chat(
            "List all .java files in the current directory"
        );
    }
}
```

### Example 5: Async & Streaming

```java
import java.util.concurrent.CompletableFuture;

public class AsyncStreaming {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest");

        // === ASYNC: Non-blocking chat ===
        System.out.println("Starting async request...");
        CompletableFuture<String> future = client.chatAsync(
            "Write a short story about a robot"
        );

        // Do other work while waiting
        System.out.println("Doing other work...");

        // Get result when ready
        future.thenAccept(reply -> {
            System.out.println("=== Story ===");
            System.out.println(reply);
        }).join();  // Wait for completion

        // === STREAMING: Real-time tokens ===
        System.out.print("Streaming: ");
        client.streamChat(
            "Count from 5 to 1",
            chunk -> System.out.print(chunk),           // on token
            error -> System.err.println("Error: " + error)  // on error
        );
        System.out.println();

        // === PARALLEL REQUESTS ===
        CompletableFuture<String> q1 = client.chatAsync("What's 2+2?");
        CompletableFuture<String> q2 = client.chatAsync("What's 3+3?");
        CompletableFuture<String> q3 = client.chatAsync("What's 4+4?");

        CompletableFuture.allOf(q1, q2, q3).join();

        System.out.println("2+2 = " + q1.join());
        System.out.println("3+3 = " + q2.join());
        System.out.println("4+4 = " + q3.join());
    }
}
```

### Example 6: Token Tracking & Context Management

```java
public class TokenTracking {
    public static void main(String[] args) {
        LLMClient client = LLMClient.openAI("gpt-4o", "sk-your-key");

        // Before chat
        System.out.println("Initial: " + client.getContextInfo().summary());

        // Do several chats
        client.chat("Explain quantum computing in detail");
        client.chat("Now explain entanglement");
        client.chat("What about superposition?");

        // Check context usage
        var info = client.getContextInfo();
        System.out.println("\n=== Context Usage ===");
        System.out.println("Model: " + info.model());
        System.out.println("Used: " + info.usedTokens() + " / " + info.totalLimit());
        System.out.println("Remaining: " + info.remainingTokens());
        System.out.println("Usage: " + String.format("%.1f%%", info.usagePercent()));

        // Smart context management
        if (client.getTokenTracker().isNearLimit()) {
            System.out.println("\nContext near limit! Clearing history...");
            client.clearHistory();
        }

        // Or just print it nicely
        client.printContextInfo();
    }
}
```

### Example 7: Builder Pattern with Full Config

```java
public class BuilderExample {
    public static void main(String[] args) {
        LLMClient client = LLMClient.builder()
            .config(ClientConfig.of(Provider.OLLAMA).model("llama3.2:latest"))
            .history(new ConversationHistory(50))  // keep last 50 messages
            .retry(new LLMClient.RetryConfig(5,
                java.time.Duration.ofMillis,
                2.0,
                java.time.Duration.ofSeconds(30)))
            .logger(SimpleLogger.get("MyApp").setLevel(SimpleLogger.Level.DEBUG))
            .build();

        // Or with Redis history
        LLMClient redisClient = LLMClient.builder()
            .config(ClientConfig.of(Provider.OPENAI).model("gpt-4o-mini").apiKey("sk-..."))
            .history(new RedisHistory.withJedis("localhost", 6379, "my-session"))
            .build();
    }
}
```

### Example 8: Multi-Tool Chain

```java
public class ToolChain {
    public static void main(String[] args) {
        LLMClient client = LLMClient.openAI("gpt-4o", "sk-your-key");

        // Register multiple tools that work together
        client.tool("search_files", "Find files matching a glob pattern",
            args -> {
                String path = args.get("path").toString();
                String pattern = args.get("pattern").toString();
                return "Found: " + path + "/" + pattern;
            },
            Map.of(
                "path", new ToolRegistry.ParamInfo("path", "Directory", true, String.class),
                "pattern", new ToolRegistry.ParamInfo("pattern", "Glob pattern", true, String.class)
            ));

        client.tool("read_file", "Read a file",
            args -> {
                String path = args.get("path").toString();
                return "File content: (would read " + path + ")";
            },
            Map.of("path", new ToolRegistry.ParamInfo("path", "File path", true, String.class)));

        // Complex multi-tool request
        String reply = client.chat(
            "Find all .java files in /project, read the largest one, and summarize it"
        );
        System.out.println(reply);
    }
}
```

### Example 9: Error Handling

```java
public class ErrorHandling {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest");

        // Sync with error handling
        try {
            String reply = client.chat("Hello!");
            System.out.println(reply);
        } catch (Exception e) {
            System.err.println("Chat failed: " + e.getMessage());
        }

        // Async with error handling
        client.chatAsync("Tell me a joke")
            .thenAccept(reply -> System.out.println(reply))
            .exceptionally(ex -> {
                System.err.println("Error: " + ex.getMessage());
                return null;
            });

        // Enable debug logging to diagnose issues
        client.setLogLevel(SimpleLogger.Level.DEBUG);
        SimpleLogger.get("LLMClient").setLevel(SimpleLogger.Level.TRACE);
    }
}
```

### Example 10: All Features Combined

```java
public class FullFeatured {
    public static void main(String[] args) {
        // Create client with all features enabled
        LLMClient client = LLMClient.openAI("gpt-4o", "sk-your-key")
            .setLogLevel(SimpleLogger.Level.INFO)      // Enable logging
            .withRedisHistory("full-demo-session")       // Persistent history
            .withSystemTools();                         // Built-in tools

        // Check initial context
        System.out.println("Starting: " + client.getContextInfo().summary());

        // Register custom tools
        client.tool("calculate", "Evaluate math",
            args -> eval(args.get("expression").toString()),
            Map.of("expression", new ToolRegistry.ParamInfo(
                "expression", "Math expression", true, String.class)));

        // Chat
        String reply = client.chat(
            "Read /etc/hostname if it exists, calculate 100*100, and search for 'Java 21'"
        );

        System.out.println("\n=== Final Context ===");
        client.printContextInfo();

        System.out.println("\n=== Reply ===");
        System.out.println(reply);
    }

    static double eval(String expr) {
        try {
            return (double) new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript").eval(expr);
        } catch (Exception e) { return 0; }
    }
}
```

---

## API Reference

### Factory Methods

| Provider | Method | API Key? |
|----------|--------|----------|
| Ollama | `LLMClient.ollama("model")` | No |
| LM Studio | `LLMClient.lmStudio("model")` | No |
| vLLM | `LLMClient.vllm("model")` | No |
| Jan | `LLMClient.jan("model")` | No |
| OpenAI | `LLMClient.openAI("gpt-4o", "sk-...")` | Yes |
| Claude | `LLMClient.claude("claude-3-5-sonnet", "sk-ant-...")` | Yes |
| DeepSeek | `LLMClient.deepSeek("deepseek-chat", "sk-...")` | Yes |
| NVIDIA | `LLMClient.nvidia("meta/llama-3.1-70b", "nv-...")` | Yes |
| Groq | `LLMClient.groq("llama-3.1-70b", "gsk-...")` | Yes |
| Mistral | `LLMClient.mistral("mistral-large", "key")` | Yes |
| OpenRouter | `LLMClient.openRouter("model", "sk-or-...")` | Yes |

### Client Methods

| Method | Description |
|--------|-------------|
| `chat(String)` | Send message, blocking |
| `chat(String, Map)` | With system prompt, temperature |
| `chatAsync(String)` | Non-blocking, returns CompletableFuture |
| `streamChat(String, Consumer)` | Stream tokens to consumer |
| `tool(name, desc, handler)` | Register tool with lambda |
| `tool(name, desc, handler, params)` | Register with param types |
| `registerTools(Object)` | Auto-register all @LLMTool methods |
| `withSystemTools()` | Register built-in file/web/bash tools |
| `withRedisHistory(id)` | Use Redis for conversation history |
| `withMemoryHistory()` | Default in-memory history |
| `getContextInfo()` | Get token usage & context window |
| `getTokenTracker()` | Get TokenTracker for detailed stats |
| `printContextInfo()` | Print formatted usage stats |
| `clearHistory()` | Clear conversation history |
| `setLogLevel(Level)` | Set logging level |
| `withRetry(config)` | Configure retry behavior |

### @LLMTool Annotation

```java
@LLMTool(
    name = "tool_name",           // Tool name (defaults to method name)
    description = "Does X",      // Description (defaults to method name)
    maxRetries = 3,              // Retry attempts (default 3, 0 = disable)
    retryDelayMs = 500,          // Initial delay ms (default 500)
    backoffMultiplier = 2.0,     // Backoff multiplier (default 2.0)
    maxRetryDelayMs = 10000      // Max delay ms (default 10000)
)
```

### @ToolParam Annotation

```java
@ToolParam(
    name = "param_name",         // Parameter name
    description = "What it is",  // Description
    required = true              // Required? (default true)
)
```

---

## Built-in System Tools

Enable with: `client.withSystemTools()`

| Tool | Args | Description |
|------|------|-------------|
| `read_file` | `path` | Read entire file contents |
| `write_file` | `path`, `content` | Write/overwrite file |
| `create_file` | `path` | Create empty file |
| `append_file` | `path`, `content` | Append to file |
| `delete_file` | `path` | Delete file or directory |
| `list_dir` | `path`, `recursive` | List directory contents |
| `find_files` | `path`, `pattern` | Find files by glob |
| `grep` | `path`, `pattern` | Search text in files |
| `path_exists` | `path` | Check if path exists |
| `file_info` | `path` | Get file metadata |
| `web_search` | `query`, `limit` | Search the web |
| `fetch_webpage` | `url`, `max_length` | Fetch webpage content |
| `run_bash` | `command`, `cwd` | Execute shell command |

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
│  .chat() / .chatAsync() / .streamChat()           │
│  .getContextInfo() / .withRedisHistory()           │
│  TokenTracker + SimpleLogger (built-in)             │
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

---

## Project Structure

```
src/main/java/in/simpletools/llm/framework/
├── client/
│   ├── LLMClient.java           # Main client
│   └── LLMClientFactory.java    # Factory shortcuts
├── config/
│   ├── Provider.java             # 12+ provider enum
│   └── ClientConfig.java        # Configuration
├── model/
│   ├── Message.java, LLMRequest.java, LLMResponse.java
│   ├── Tool.java, ToolCall.java
├── adapter/
│   ├── ProviderAdapter.java     # Adapter interface
│   ├── OllamaAdapter.java, OpenAIAdapter.java, ClaudeAdapter.java
├── tool/
│   ├── LLMTool.java            # @LLMTool annotation
│   ├── ToolParam.java          # @ToolParam annotation
│   ├── ToolRegistry.java       # Auto-discovery + retry
│   └── OllamaTool.java        # Legacy annotation
├── history/
│   ├── ConversationHistory.java  # In-memory
│   ├── ConversationHistoryStore.java
│   ├── RedisHistory.java       # Redis-backed
│   └── TokenTracker.java      # Token tracking
├── tools/
│   └── SystemTools.java        # Built-in tools
└── utils/
    └── SimpleLogger.java      # Built-in logger
```

---

## Troubleshooting

### `Connection refused` (Local Providers)
```bash
# Check Ollama is running
ollama serve

# Test manually
curl http://localhost:11434/api/tags
```

### `401 Unauthorized` (Cloud)
```bash
# Verify key is set
echo $OPENAI_API_KEY

# Test the key
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

### Tool not being called
1. Tool description must be clear and descriptive
2. Parameter names must match what the LLM expects
3. Some models don't support tools (use OpenAI or Claude)

### Out of context window
```java
// Check usage
client.printContextInfo();

// Clear when near limit
if (client.getTokenTracker().isNearLimit()) {
    client.clearHistory();
}
```

---

## License

MIT License — See [LICENSE](LICENSE)

**Repository**: https://github.com/simpletoolsindia/llm-client-framework
**Issues**: https://github.com/simpletoolsindia/llm-client-framework/issues
