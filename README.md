# LLM Client Framework for Java

A unified Java framework for interacting with multiple Large Language Model (LLM) providers through a single, consistent API.

**Package**: `in.simpletools.llm-client-framework`  
**Latest Version**: `1.0.1`  
**Repository**: GitHub Packages

---

## Features

- **12+ LLM Providers** - Local and cloud
- **Single API** - Consistent interface across all providers
- **Function Calling** - Native tool/tool support
- **Streaming** - Real-time streaming responses
- **Conversation History** - Built-in history management

---

## Supported Providers

| Provider | Type | Default URL |
|----------|------|-------------|
| **Ollama** | Local | `localhost:11434` |
| **LM Studio** | Local | `localhost:1234` |
| **vLLM** | Local | `localhost:8000` |
| **Jan** | Local | `localhost:1337` |
| **OpenAI** | Cloud | `api.openai.com` |
| **DeepSeek** | Cloud | `api.deepseek.com` |
| **NVIDIA** | Cloud | `integrate.api.nvidia.com` |
| **OpenRouter** | Cloud | `openrouter.ai` |
| **Claude** | Cloud | `api.anthropic.com` |
| **Mistral** | Cloud | `api.mistral.ai` |
| **Groq** | Cloud | `api.groq.com` |

---

## Installation

### GitHub Packages

**1. Add repository to your project:**

For Gradle (`build.gradle` or `build.gradle.kts`):
```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/simpletoolsindia/llm-client-framework")
        credentials {
            username = "simpletoolsindia"
            password = System.getenv("GITHUB_TOKEN") ?: ""
        }
    }
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.1'
}
```

For Maven (`pom.xml`):
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/simpletoolsindia/llm-client-framework</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>in.simpletools</groupId>
        <artifactId>llm-client-framework</artifactId>
        <version>1.0.1</version>
    </dependency>
</dependencies>
```

**Note**: For Maven/Gradle authentication, you need a GitHub Personal Access Token with `packages:read` scope. Set it as:
- Environment variable: `GITHUB_TOKEN`
- Or in `~/.gradle/gradle.properties`: `gpr.key=YOUR_TOKEN`

### Local Build

```bash
git clone https://github.com/simpletoolsindia/llm-client-framework.git
cd llm-client-framework
gradle build
gradle install
```

---

## Quick Start

```java
import in.simpletools.llm.framework.client.*;

public class Main {
    public static void main(String[] args) {
        // Create client
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");

        // Chat
        String response = client.chat("Hello!");
        System.out.println(response);
    }
}
```

---

## Usage Examples

### Basic Chat

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");
String response = client.chat("What is Java?");
```

### With System Prompt

```java
String response = client.chat("Explain recursion", Map.of(
    "system", "You are a computer science professor."
));
```

### Streaming

```java
client.streamChat("Write a story", chunk -> System.out.print(chunk));
```

### With Tools (Function Calling)

```java
client.registerTool("calculate", "Math expression",
    args -> {
        String expr = (String) args.get("expression");
        return new javax.script.ScriptEngineManager()
            .getEngineByName("JavaScript").eval(expr);
    },
    Map.of("expression", new ToolRegistry.ParamInfo(
        "expression", "Math expression", true, String.class))
);

String response = client.chat("What is 25 * 4 + 10?");
```

### Cloud Providers

```java
// OpenAI
LLMClient openai = LLMClientFactory.openAI("gpt-4o", apiKey);

// Claude
LLMClient claude = LLMClientFactory.claude("claude-sonnet-4-20250514", apiKey);

// DeepSeek
LLMClient deepseek = LLMClientFactory.deepSeek("deepseek-chat", apiKey);

// NVIDIA NIM
LLMClient nvidia = LLMClientFactory.nvidia("meta/llama-3.1-70b-instruct", apiKey);

// OpenRouter
LLMClient openrouter = LLMClientFactory.openRouter("anthropic/claude-3.5-sonnet", apiKey);
```

### Conversation History

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

client.chat("My name is Alice");
String response = client.chat("What is my name?");

System.out.println(client.getHistory().size()); // 4 messages

client.clearHistory(); // Start fresh
```

---

## Environment Variables

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export DEEPSEEK_API_KEY=sk-...
export NVIDIA_API_KEY=nv-...
export OPENROUTER_API_KEY=sk-or-...
```

---

## API Reference

### LLMClientFactory

| Method | Description |
|--------|-------------|
| `ollama()` / `ollama(model)` | Ollama (local) |
| `lmStudio()` / `lmStudio(model)` | LM Studio (local) |
| `vllm()` / `vllm(model)` | vLLM (local) |
| `jan()` / `jan(model)` | Jan (local) |
| `openAI(model, apiKey)` | OpenAI (cloud) |
| `claude(model, apiKey)` | Claude (cloud) |
| `deepSeek(model, apiKey)` | DeepSeek (cloud) |
| `nvidia(model, apiKey)` | NVIDIA NIM (cloud) |
| `openRouter(model, apiKey)` | OpenRouter (cloud) |
| `mistral(model, apiKey)` | Mistral (cloud) |
| `groq(model, apiKey)` | Groq (cloud) |

### LLMClient

| Method | Description |
|--------|-------------|
| `chat(String)` | Send message, get response |
| `chat(String, Map)` | Send with options (system, temperature, etc.) |
| `chat(Message)` | Send structured message |
| `streamChat(String, Consumer)` | Streaming response |
| `registerTool(...)` | Register function tool |
| `getHistory()` | Get conversation history |
| `clearHistory()` | Clear all messages |
| `clearLastN(int n)` | Clear last N messages |

---

## Architecture

```
LLMClientFactory
       │
       ▼
    LLMClient
       │
       ▼
 ProviderAdapter (Strategy Pattern)
       │
   ┌───┼───┬─────┬─────┐
   │   │   │     │     │
Ollama OpenAI Claude Generic
```

---

## Project Structure

```
src/main/java/in/simpletools/llm/framework/
├── LLMFramework.java           # Entry point / DSL
├── client/
│   ├── LLMClient.java         # Main client
│   └── LLMClientFactory.java   # Factory methods
├── config/
│   ├── Provider.java          # Provider enum
│   └── ClientConfig.java      # Configuration
├── model/
│   ├── Message.java
│   ├── LLMRequest.java
│   ├── LLMResponse.java
│   ├── Tool.java
│   └── ToolCall.java
├── adapter/
│   ├── ProviderAdapter.java   # Adapter interface
│   ├── OpenAIAdapter.java     # OpenAI-compatible
│   ├── OllamaAdapter.java     # Ollama-specific
│   └── ClaudeAdapter.java     # Claude API
├── tool/
│   ├── ToolRegistry.java
│   ├── OllamaTool.java
│   └── ToolParam.java
└── history/
    └── ConversationHistory.java
```

---

## Build & Run

```bash
# Build
gradle build

# Run example
gradle run

# Publish to GitHub Packages
gradle publish
```

---

## License

MIT License - See [LICENSE](LICENSE)

---

**GitHub**: https://github.com/simpletoolsindia/llm-client-framework
**Package**: https://github.com/simpletoolsindia/llm-client-framework/packages
