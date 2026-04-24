# LLM Client Framework for Java

A unified Java framework for interacting with multiple Large Language Model (LLM) providers through a single, consistent API.

**Package**: `in.simpletools.llm-client-framework`  
**Repository**: GitHub Packages

---

## Features

- **12+ LLM Providers** - Local and cloud
- **Single API** - Consistent interface
- **Function Calling** - Native tool support
- **Streaming** - Real-time responses
- **Conversation History** - Built-in management

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

### GitHub Packages (Available Now)

**1. Create a GitHub Personal Access Token (PAT):**
- Go to: https://github.com/settings/tokens/new
- Select scopes: `repo` and `packages`
- Copy the generated token

**2. Configure Gradle credentials:**

Add to `~/.gradle/gradle.properties`:
```properties
gpr.user=simpletoolsindia
gpr.key=YOUR_GITHUB_PAT_HERE
```

**3. Add repository to your project:**

For Gradle (`build.gradle`):
```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/simpletoolsindia/llm-client-framework")
        credentials {
            username = project.findProperty("gpr.user") ?: ""
            password = project.findProperty("gpr.key") ?: ""
        }
    }
}

dependencies {
    implementation 'in.simpletools:llm-client-framework:1.0.0'
}
```

For Maven (`pom.xml`):
```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/simpletoolsindia/llm-client-framework</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>in.simpletools</groupId>
        <artifactId>llm-client-framework</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

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

### With Tools

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
```

---

## Environment Variables

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export DEEPSEEK_API_KEY=sk-...
```

---

## API Reference

### LLMClientFactory

| Method | Description |
|--------|-------------|
| `ollama(model)` | Ollama (local) |
| `openAI(model, apiKey)` | OpenAI (cloud) |
| `claude(model, apiKey)` | Claude (cloud) |
| `deepSeek(model, apiKey)` | DeepSeek (cloud) |
| `nvidia(model, apiKey)` | NVIDIA (cloud) |
| `openRouter(model, apiKey)` | OpenRouter (cloud) |
| `lmStudio(model)` | LM Studio (local) |
| `vllm(model)` | vLLM (local) |
| `jan(model)` | Jan (local) |

### LLMClient

| Method | Description |
|--------|-------------|
| `chat(String)` | Send message, get response |
| `chat(String, Map)` | With options |
| `chat(Message)` | Send structured message |
| `streamChat(String, Consumer)` | Streaming |
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
├── LLMFramework.java        # Entry point
├── client/
│   ├── LLMClient.java       # Main client
│   └── LLMClientFactory.java # Factory
├── config/
│   ├── Provider.java        # Enum
│   └── ClientConfig.java    # Config
├── model/
│   ├── Message.java
│   ├── LLMRequest.java
│   ├── LLMResponse.java
│   ├── Tool.java
│   └── ToolCall.java
├── adapter/
│   ├── ProviderAdapter.java
│   ├── OpenAIAdapter.java
│   ├── OllamaAdapter.java
│   └── ClaudeAdapter.java
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
gradle build
gradle run
```

---

## Publishing

### GitHub Packages

```bash
# 1. Create a GitHub Personal Access Token (PAT) with 'write:packages' scope
#    https://github.com/settings/tokens/new

# 2. Add credentials to ~/.gradle/gradle.properties
echo "gpr.user=simpletoolsindia" >> ~/.gradle/gradle.properties
echo "gpr.key=YOUR_GITHUB_PAT_HERE" >> ~/.gradle/gradle.properties

# 3. Publish
gradle publish
```

### Maven Central

Maven Central publishing is configured but requires a paid Sonatype subscription. Once activated, artifacts will be available at Maven Central.

---

## License

MIT License - See [LICENSE](LICENSE)

---

**GitHub**: https://github.com/simpletoolsindia/llm-client-framework
