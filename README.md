# LLM Client Framework for Java

A high-performance, functional Java framework for interacting with **12+ LLM providers** through a single, consistent API. Built with modern Java patterns: CompletableFuture for async, thread-safe tool execution, and configurable retry logic.

[![Maven Central](https://img.shields.io/badge/Maven-JitPack-green)](https://jitpack.io/#simpletoolsindia/llm-client-framework)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

---

## Features

- **12+ Providers** — Local (Ollama, LM Studio, vLLM, Jan) and Cloud (OpenAI, Claude, DeepSeek, etc.)
- **Async/Await** — CompletableFuture-based async chat for high performance
- **Thread-Safe Tool Execution** — Parallel tool execution with configurable retry
- **Streaming** — Real-time token-by-token responses
- **Conversation History** — Automatic context management
- **Builder Pattern** — Fluent API for client configuration
- **Zero External Dependencies** — Uses only Java standard library + Gson

---

## Installation

### JitPack (Recommended - Free)

```groovy
// build.gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.simpletoolsindia:llm-client-framework:v1.0.2'
}
```

### GitHub Packages

```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/simpletoolsindia/llm-client-framework")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: ""
            password = System.getenv("GITHUB_TOKEN") ?: ""
        }
    }
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.2'
}
```

---

## Quick Start

### Basic Chat

```java
import in.simpletools.llm.framework.client.*;

public class Main {
    public static void main(String[] args) {
        // Create client with Ollama (local, free)
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");

        // Simple chat
        String response = client.chat("What is recursion?");
        System.out.println(response);
    }
}
```

### Cloud Providers

```java
// OpenAI
LLMClient openai = LLMClientFactory.openAI("gpt-4o-mini", "sk-...");

// Claude
LLMClient claude = LLMClientFactory.claude("claude-3-5-sonnet", "sk-ant-...");

// DeepSeek
LLMClient deepseek = LLMClientFactory.deepSeek("deepseek-chat", "sk-...");
```

---

## Comprehensive Examples

### Example 1: Multi-Turn Conversation

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// First message
client.chat("My name is Ravi and I live in Bangalore.");
String answer = client.chat("What city do I live in?");
System.out.println(answer); // Bangalore

// View conversation history
System.out.println("Messages: " + client.getHistory().size());

// Clear and start fresh
client.clearHistory();
```

### Example 2: System Prompt (Persona)

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

String response = client.chat("Explain photosynthesis", Map.of(
    "system", "You are a friendly science teacher who uses simple words."
));
```

### Example 3: Streaming Responses

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

System.out.print("Generating: ");
client.streamChat("Write a haiku about coding", chunk -> {
    System.out.print(chunk); // Tokens appear in real-time
});
```

### Example 4: Async Chat (Non-blocking)

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Non-blocking - returns immediately
CompletableFuture<String> future = client.chatAsync("Tell me a story");

// Do other work while waiting...
System.out.println("Waiting for response...");

future.thenAccept(response -> {
    System.out.println("Story: " + response);
});
```

### Example 5: Function Calling with Retry

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Register calculator tool with automatic retry
client.registerTool(
    "calculate",
    "Evaluates a mathematical expression",
    args -> {
        String expr = (String) args.get("expression");
        return new javax.script.ScriptEngineManager()
            .getEngineByName("JavaScript")
            .eval(expr);
    },
    Map.of("expression", new ToolRegistry.ParamInfo(
        "expression", "Math expression", true, String.class))
);

// LLM will call the tool automatically with retry on failure
String response = client.chat(
    "What is (125 + 375) / 25? Use the calculate tool."
);
```

### Example 6: Custom Retry Configuration

```java
import java.time.Duration;

LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Customize retry behavior
RetryConfig retryConfig = new RetryConfig(
    5,                    // max attempts
    Duration.ofMillis, // initial delay
    2.0,                  // backoff multiplier
    Duration.ofSeconds(30)   // max delay
);

client.withRetry(retryConfig);
```

### Example 7: Builder Pattern

```java
LLMClient client = LLMClient.builder()
    .config(ClientConfig.of(Provider.OLLAMA).model("gemma4:latest"))
    .retry(RetryConfig.defaults())
    .build();
```

### Example 8: Multiple Tools

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Calculator tool
client.registerTool("calculate", "Math expression", args -> {
    return new javax.script.ScriptEngineManager()
        .getEngineByName("JavaScript")
        .eval((String) args.get("expression"));
}, Map.of("expression", new ToolRegistry.ParamInfo(
    "expression", "Math expression", true, String.class)));

// File search tool
client.registerTool("search_files", "Search files", args -> {
    String pattern = (String) args.get("pattern");
    return "Found: file1.java, file2.java"; // Implement actual logic
}, Map.of("pattern", new ToolRegistry.ParamInfo(
    "pattern", "File pattern", true, String.class)));

// LLM chooses appropriate tool
String response = client.chat(
    "Calculate sqrt(144) and then find all .java files"
);
```

### Example 9: Low-Level API Access

```java
import in.simpletools.llm.framework.model.*;

LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Build custom request
LLMRequest request = LLMRequest.builder()
    .model("gemma4:latest")
    .addMessage(Message.ofSystem("You are a helpful assistant"))
    .addMessage(Message.ofUser("What is 2+2?"))
    .temperature(0.7)
    .build();
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Your Application                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        LLMClient                             │
│  • chat() / chatAsync() / streamChat()                       │
│  • registerTool() / withRetry()                              │
│  • CompletableFuture for async operations                    │
│  • Thread-safe tool execution with retry                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ProviderAdapter                           │
│              (Strategy Pattern)                              │
│                                                              │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────────┐  │
│   │ Ollama   │  │ OpenAI   │  │ Claude   │  │ Custom      │  │
│   └──────────┘  └──────────┘  └──────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**Design Patterns Used:**
- **Factory Pattern** — `LLMClientFactory` creates pre-configured clients
- **Builder Pattern** — `LLMClient.builder()` for fluent configuration
- **Strategy Pattern** — `ProviderAdapter` for pluggable LLM backends
- **Async/Await** — `CompletableFuture` for non-blocking operations
- **Retry Pattern** — Configurable exponential backoff for tool execution

---

## API Reference

### Factory Methods

```java
// Local providers (no API key)
LLMClient.ollama("model")
LLMClient.ollama("http://host:11434", "model")
LLMClient.lmStudio("model")
LLMClient.vllm("model")
LLMClient.jan("model")

// Cloud providers (API key required)
LLMClient.openAI("gpt-4o", "sk-...")
LLMClient.claude("claude-3-5-sonnet", "sk-ant-...")
LLMClient.deepSeek("deepseek-chat", "sk-...")
LLMClient.nvidia("meta/llama-3.1-70b", "nv-...")
LLMClient.openRouter("anthropic/claude-3.5-sonnet", "sk-or-...")
LLMClient.groq("llama-3.1-70b-versatile", "key")
LLMClient.mistral("mistral-large-latest", "key")
```

### Client Methods

| Method | Description |
|--------|-------------|
| `chat(String)` | Send message, get text response (blocking) |
| `chat(String, Map<String,String>)` | With options (system, temperature) |
| `chat(Message)` | Send structured message |
| `chatAsync(String)` | Async version returning CompletableFuture |
| `streamChat(String, Consumer)` | Stream tokens to consumer (non-blocking) |
| `registerTool(...)` | Register a function for LLM to call |
| `withRetry(RetryConfig)` | Configure retry behavior |
| `getHistory()` | Get ConversationHistory |
| `clearHistory()` | Clear conversation history |

### RetryConfig

```java
RetryConfig defaults = RetryConfig.defaults();  // 3 attempts, 500ms initial

RetryConfig custom = new RetryConfig(
    5,                    // maxAttempts
    Duration.ofMillis, // initialDelay
    2.0,                  // backoffMultiplier
    Duration.ofSeconds(30)   // maxDelay
);
```

---

## Supported Providers

### Local (Free, Privacy-First)

| Provider | Default URL | Best For |
|----------|-------------|----------|
| **Ollama** | `localhost:11434` | General purpose |
| **LM Studio** | `localhost:1234` | Desktop GPU |
| **vLLM** | `localhost:8000` | High throughput |
| **Jan** | `localhost:1337` | Local-first |

### Cloud (API Key Required)

| Provider | Base URL | Notable Models |
|----------|----------|----------------|
| **OpenAI** | `api.openai.com` | GPT-4o, o1 |
| **Claude** | `api.anthropic.com` | Claude 3.5 Sonnet |
| **DeepSeek** | `api.deepseek.com` | DeepSeek Coder, V3 |
| **NVIDIA NIM** | `integrate.api.nvidia.com` | Llama 3.1 |
| **OpenRouter** | `openrouter.ai` | 100+ models |
| **Mistral** | `api.mistral.ai` | Mixtral, Codestral |
| **Groq** | `api.groq.com` | Fast Llama |

---

## Project Structure

```
src/main/java/in/simpletools/llm/framework/
├── client/
│   ├── LLMClient.java              # Main client with async support
│   └── LLMClientFactory.java        # Static factory methods
├── config/
│   ├── Provider.java               # Enum for all providers
│   └── ClientConfig.java           # Configuration builder
├── model/
│   ├── Message.java                # Chat messages
│   ├── LLMRequest.java             # Request builder
│   ├── LLMResponse.java            # Response object
│   ├── Tool.java                   # Tool definitions
│   └── ToolCall.java              # Tool invocation
├── adapter/
│   ├── ProviderAdapter.java        # Adapter interface
│   ├── OllamaAdapter.java          # Ollama implementation
│   ├── OpenAIAdapter.java          # OpenAI-compatible
│   └── ClaudeAdapter.java          # Claude API
├── tool/
│   ├── ToolRegistry.java           # Tool management
│   └── ToolParam.java             # Parameter definitions
└── history/
    └── ConversationHistory.java     # Message history
```

---

## Environment Setup

### Ollama (Local - Recommended for Development)

```bash
# Install
curl -fsSL https://ollama.com/install.sh | sh

# Pull model
ollama pull gemma4:latest

# Run server
ollama serve  # Runs on localhost:11434
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
# Build project
gradle build

# Run demo
gradle run

# Clean
gradle clean
```

---

## Performance Features

### Async Operations
```java
// Non-blocking chat
CompletableFuture<String> future = client.chatAsync("Generate report");

// Process multiple requests in parallel
List<CompletableFuture<String>> requests = List.of(
    client.chatAsync("Task 1"),
    client.chatAsync("Task 2"),
    client.chatAsync("Task 3")
);

CompletableFuture.allOf(requests.toArray(new CompletableFuture[0]))
    .thenAccept(v -> System.out.println("All done!"));
```

### Thread-Safe Tool Execution
- Tools execute in parallel using cached thread pool
- Automatic retry with exponential backoff
- Configurable max attempts and delays

---

## Troubleshooting

### Connection Refused (Local Providers)
1. Ensure Ollama/LM Studio is running
2. Check URL: `ollama serve` or custom host

### 401 Unauthorized (Cloud)
1. Verify API key is valid
2. Check key has required permissions

### Tool Not Being Called
1. Ensure tool description is clear
2. Check parameter types match
3. Some models don't support function calling

---

## License

MIT License - See [LICENSE](LICENSE)

---

**Repository**: https://github.com/simpletoolsindia/llm-client-framework
**Issues**: https://github.com/simpletoolsindia/llm-client-framework/issues