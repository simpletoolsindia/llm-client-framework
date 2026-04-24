# LLM Client Framework for Java

A lightweight, unified Java framework for interacting with **12+ LLM providers** through a single, consistent API. Supports local models (Ollama, LM Studio, vLLM) and cloud providers (OpenAI, Claude, DeepSeek, etc.).

[![Maven Central](https://img.shields.io/badge/Maven-JitPack-green)](https://jitpack.io/#simpletoolsindia/llm-client-framework)
[![Java](https://img.shields.io/badge/Java-21+-blue)](https://www.java.com/)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

---

## Features

- **12+ Providers** — Local and cloud LLMs in one library
- **Single API** — Switch providers without changing code
- **Function Calling** — Register Java methods as LLM tools
- **Streaming** — Real-time token-by-token responses
- **Conversation History** — Automatic context management
- **Zero Dependencies** — Uses only Java standard library + Gson

---

## Supported Providers

### Local (Free, Privacy-First)

| Provider | Default URL | Best For |
|----------|-------------|----------|
| **Ollama** | `localhost:11434` | General purpose, wide model support |
| **LM Studio** | `localhost:1234` | Desktop GPU acceleration |
| **vLLM** | `localhost:8000` | High-throughput serving |
| **Jan** | `localhost:1337` | Local-first, no cloud |

### Cloud (API Key Required)

| Provider | Base URL | Notable Models |
|----------|----------|----------------|
| **OpenAI** | `api.openai.com` | GPT-4o, GPT-4o-mini, o1 |
| **Claude** | `api.anthropic.com` | Claude 3.5 Sonnet, Claude 3 Opus |
| **DeepSeek** | `api.deepseek.com` | DeepSeek Coder, V3 |
| **NVIDIA NIM** | `integrate.api.nvidia.com` | Llama 3.1, Mistral |
| **OpenRouter** | `openrouter.ai` | Access 100+ models |
| **Mistral** | `api.mistral.ai` | Mixtral, Codestral |
| **Groq** | `api.groq.com` | Fast inference, Llama |

---

## Installation

### Option 1: JitPack (Recommended - Free)

```groovy
// build.gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.simpletoolsindia:llm-client-framework:v1.0.2'
}
```

### Option 2: GitHub Packages

```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/YOUR_USERNAME/llm-client-framework")
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

### Option 3: Local Build

```bash
git clone https://github.com/simpletoolsindia/llm-client-framework.git
cd llm-client-framework
gradle build
gradle install
```

---

## Quick Start

### 1. Chat with Local Ollama

```java
import in.simpletools.llm.framework.client.*;

public class Main {
    public static void main(String[] args) {
        // Create client - uses Ollama by default
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");

        // Simple chat
        String response = client.chat("What is recursion?");
        System.out.println(response);
    }
}
```

### 2. Chat with Cloud Provider

```java
// OpenAI example
LLMClient openai = LLMClientFactory.openAI("gpt-4o-mini", "sk-...");

// Claude example
LLMClient claude = LLMClientFactory.claude("claude-sonnet-4-20250514", "sk-ant-...");

// DeepSeek example
LLMClient deepseek = LLMClientFactory.deepSeek("deepseek-chat", "sk-...");

String response = claude.chat("Explain quantum entanglement in simple terms");
System.out.println(response);
```

---

## Comprehensive Examples

### Example 1: Multi-Turn Conversation

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// First message
String answer1 = client.chat("My name is Ravi and I live in Bangalore.");
System.out.println("AI: " + answer1);

// Follow-up question (AI remembers context)
String answer2 = client.chat("What city do I live in?");
System.out.println("AI: " + answer2);
// Output: Bangalore (from conversation history)

// View conversation
System.out.println("Messages: " + client.getHistory().size());
// Output: 4 messages (user, assistant, user, assistant)

// Clear and start fresh
client.clearHistory();
```

### Example 2: System Prompt (Persona)

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Set AI personality via options
String response = client.chat("Explain photosynthesis", Map.of(
    "system", "You are a friendly science teacher who uses simple words."
));

System.out.println(response);
```

### Example 3: Streaming Responses

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

System.out.print("Writing story: ");
client.streamChat("Write a short story about a robot learning to dance", chunk -> {
    System.out.print(chunk);
});
System.out.println();
```

### Example 4: Function Calling (Calculator Tool)

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Register a tool the LLM can call
client.registerTool(
    "calculate",
    "Evaluates a mathematical expression and returns the result",
    args -> {
        String expr = (String) args.get("expression");
        try {
            // Use JavaScript engine for math
            return new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript")
                .eval(expr);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    },
    java.util.Map.of(
        "expression", new ToolRegistry.ParamInfo(
            "expression",
            "The math expression to evaluate (e.g., '25 * 4 + 10')",
            true,
            String.class
        )
    )
);

// LLM will call the calculate tool automatically
String response = client.chat("What is 25 multiplied by 4, plus 10?");
System.out.println(response);
// LLM recognizes it needs math -> calls calculate tool -> returns 110
```

### Example 5: File Search Tool

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Register a file search tool
client.registerTool(
    "search_files",
    "Search for files matching a pattern in a directory",
    args -> {
        String dir = (String) args.get("directory");
        String pattern = (String) args.get("pattern");
        // Implementation logic here
        return "Found: file1.java, file2.java";
    },
    java.util.Map.of(
        "directory", new ToolRegistry.ParamInfo("directory", "Directory to search", true, String.class),
        "pattern", new ToolRegistry.ParamInfo("pattern", "File pattern (e.g., '*.java')", true, String.class)
    )
);

String response = client.chat("Find all Java files in my src folder");
System.out.println(response);
```

### Example 6: Using Different Providers

```java
public class ProviderDemo {
    public static void main(String[] args) {
        // Local - No API key needed
        LLMClient local = LLMClientFactory.ollama("gemma4:latest");

        // Cloud - Requires API key
        LLMClient openai = LLMClientFactory.openAI("gpt-4o", System.getenv("OPENAI_API_KEY"));
        LLMClient claude = LLMClientFactory.claude("claude-3-5-sonnet-20240620", System.getenv("ANTHROPIC_API_KEY"));
        LLMClient deepseek = LLMClientFactory.deepSeek("deepseek-chat", System.getenv("DEEPSEEK_API_KEY"));
        LLMClient groq = LLMClientFactory.groq("llama-3.1-70b-versatile", System.getenv("GROQ_API_KEY"));

        // All use the same API!
        System.out.println(local.chat("Hello"));
        System.out.println(claude.chat("Hello"));
    }
}
```

### Example 7: Custom Configuration

```java
import in.simpletools.llm.framework.config.*;

// Build custom client
ClientConfig config = ClientConfig.of(Provider.OLLAMA)
    .baseUrl("http://192.168.1.100:11434")  // Custom host
    .model("llama3.1:8b")                    // Specific model
    .temperature(0.7)                         // Creativity level (0-2)
    .maxTokens                         // Max response length
    .stream(true);                           // Enable streaming

LLMClient client = LLMClient.create(config);

// Or with custom base URL directly
LLMClient custom = LLMClientFactory.ollama("http://custom-host:11434", "llama3");
```

### Example 8: Low-Level API Access

```java
import in.simpletools.llm.framework.model.*;

LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Build custom message
Message userMsg = new Message(Message.Role.user, "What is 2+2?");
String response = client.chat(userMsg);

// Access raw response data
LLMRequest request = LLMRequest.builder()
    .model("gemma4:latest")
    .addMessage(Message.ofSystem("You are a math tutor"))
    .addMessage(Message.ofUser("What is 2+2?"))
    .temperature(0.5)
    .build();

System.out.println(request);
```

---

## API Reference

### Factory Methods (LLMClientFactory)

```java
// Local providers (no API key)
LLMClient.ollama()                    // Default: gemma4:latest
LLMClient.ollama("llama3.1:8b")
LLMClient.ollama("http://host:11434", "model")
LLMClient.lmStudio("model-name")
LLMClient.vllm("model-name")
LLMClient.jan("model-name")

// Cloud providers (API key required)
LLMClient.openAI("gpt-4o", "sk-...")
LLMClient.claude("claude-3-5-sonnet", "sk-ant-...")
LLMClient.deepSeek("deepseek-chat", "sk-...")
LLMClient.nvidia("meta/llama-3.1-70b", "nv-...")
LLMClient.openRouter("anthropic/claude-3.5-sonnet", "sk-or-...")
LLMClient.mistral("mistral-large-latest", "key")
LLMClient.groq("llama-3.1-70b-versatile", "key")
```

### Client Methods (LLMClient)

| Method | Description |
|--------|-------------|
| `chat(String message)` | Send message, get text response |
| `chat(String, Map<String,String>)` | With options (system, temperature, etc.) |
| `chat(Message)` | Send structured message object |
| `streamChat(String, Consumer<String>)` | Stream tokens to consumer |
| `registerTool(...)` | Register a function for LLM to call |
| `getHistory()` | Get ConversationHistory object |
| `clearHistory()` | Clear all conversation history |
| `clearLastN(int n)` | Remove last n messages |

### Tool Registration

```java
client.registerTool(
    String name,                           // Tool name (e.g., "calculate")
    String description,                    // What the tool does
    Function<Map<String,Object>, Object> handler,  // Implementation
    Map<String, ParamInfo> parameters      // Parameter definitions
);
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Your Application                      │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                     LLMClient                           │
│  • chat()                                               │
│  • streamChat()                                        │
│  • registerTool()                                      │
│  • conversation history                                │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                  ProviderAdapter                         │
│            (Strategy Pattern)                           │
│                                                          │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│   │ Ollama   │  │ OpenAI   │  │ Claude   │  │ etc... │ │
│   └──────────┘  └──────────┘  └──────────┘  └────────┘ │
└─────────────────────────────────────────────────────────┘
```

**Key Patterns:**
- **Factory Pattern** — `LLMClientFactory` creates configured clients
- **Strategy Pattern** — `ProviderAdapter` allows switching LLMs
- **Adapter Pattern** — Translates between provider-specific formats
- **Builder Pattern** — `LLMRequest.builder()` for flexible request building

---

## Project Structure

```
src/main/java/in/simpletools/llm/framework/
├── LLMFramework.java           # Fluent DSL entry point
├── client/
│   ├── LLMClient.java          # Main client class
│   └── LLMClientFactory.java   # Factory for creating clients
├── config/
│   ├── Provider.java           # Enum: all supported providers
│   └── ClientConfig.java       # Builder for client configuration
├── model/
│   ├── Message.java            # Chat message (user/assistant/system)
│   ├── LLMRequest.java         # Request builder
│   ├── LLMResponse.java        # Response object
│   ├── Tool.java               # Tool definition for function calling
│   └── ToolCall.java          # Tool invocation from LLM
├── adapter/
│   ├── ProviderAdapter.java   # Interface for all adapters
│   ├── OpenAIAdapter.java      # OpenAI-compatible API
│   ├── OllamaAdapter.java      # Ollama-specific API
│   └── ClaudeAdapter.java      # Anthropic Claude API
├── tool/
│   ├── ToolRegistry.java       # Manages registered tools
│   └── ToolParam.java          # Parameter definitions
└── history/
    └── ConversationHistory.java  # Message history management
```

---

## Environment Setup

### Ollama (Local)

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull a model
ollama pull gemma4:latest

# Run (automatically starts server on localhost:11434)
ollama run gemma4:latest
```

### API Keys

Set as environment variables:
```bash
export OPENAI_API_KEY=sk-your-key-here
export ANTHROPIC_API_KEY=sk-ant-your-key-here
export DEEPSEEK_API_KEY=sk-your-key-here
export GROQ_API_KEY=gsk_your-key-here
```

Or pass directly:
```java
LLMClient client = LLMClientFactory.openAI("gpt-4o", System.getenv("OPENAI_API_KEY"));
```

---

## Build & Development

```bash
# Build the project
gradle build

# Run the example
gradle run

# Run tests
gradle test

# Clean build artifacts
gradle clean
```

---

## Troubleshooting

### Connection Refused (Local Providers)

1. Ensure Ollama/LM Studio is running
2. Check the URL matches your setup
3. For Ollama: `ollama serve` to start the API server

### 401 Unauthorized (Cloud Providers)

1. Verify your API key is valid
2. Check key has required permissions
3. Ensure provider URL is correct

### Tool Not Being Called

1. Ensure tool description is clear and detailed
2. Check parameter types match expected values
3. Some models don't support function calling (use gpt-4, claude-3, llama3)

---

## Contributing

Contributions welcome! Please read the code style and submit PRs.

---

## License

MIT License - See [LICENSE](LICENSE)

---

**Repository**: https://github.com/simpletoolsindia/llm-client-framework
**Issues**: https://github.com/simpletoolsindia/llm-client-framework/issues