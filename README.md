# LLM Client Framework for Java

A unified, lightweight Java framework for interacting with multiple Large Language Model (LLM) providers through a single, consistent API. Built with clean architecture, factory patterns, and SOLID principles.

[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## Features

- **12+ LLM Providers** - Local and cloud providers supported
- **Single API** - No need to learn different APIs for each provider
- **Function Calling** - Native tool/tool support
- **Streaming** - Real-time streaming responses
- **Conversation History** - Built-in message history management
- **Multi-Modal** - Image support for vision-capable models
- **Clean Architecture** - Factory, Strategy, and Adapter patterns

---

## Supported Providers

### Local Providers (Free, No API Key)

| Provider | Default URL | Model Example |
|----------|-------------|---------------|
| **Ollama** | `http://localhost:11434` | `gemma4:latest`, `llama3.2` |
| **LM Studio** | `http://localhost:1234/v1` | `local-model` |
| **vLLM** | `http://localhost:8000/v1` | `mistralai/Mistral-7B` |
| **Jan** | `http://localhost:1337/v1` | `llama-3-8b` |

### Cloud Providers (API Key Required)

| Provider | Default URL | Model Example |
|----------|-------------|---------------|
| **OpenAI** | `https://api.openai.com/v1` | `gpt-4o`, `gpt-4o-mini` |
| **DeepSeek** | `https://api.deepseek.com/v1` | `deepseek-chat` |
| **NVIDIA** | `https://integrate.api.nvidia.com/v1` | `meta/llama3-70b` |
| **OpenRouter** | `https://openrouter.ai/api/v1` | `anthropic/claude-3.5-sonnet` |
| **Claude** | `https://api.anthropic.com/v1` | `claude-sonnet-4-20250514` |
| **Mistral** | `https://api.mistral.ai/v1` | `mistral-large-latest` |
| **Groq** | `https://api.groq.com/openai/v1` | `llama-3.1-70b-versatile` |

---

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/simpletoolsindia/llm-client-framework.git
cd llm-client-framework
```

### 2. Build

```bash
gradle build
```

### 3. Run Examples

```bash
gradle run
```

### Requirements

- **Java 21+** - [Download](https://adoptium.net/)
- **Gradle 9+** (included via wrapper)

---

## Quick Start Guide

### 1. Basic Chat with Ollama (Local)

```java
import com.simpletoolsindia.llm.framework.client.*;

public class QuickStart {
    public static void main(String[] args) {
        // Create client - uses gemma4:latest by default
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");
        
        // Send message and get response
        String response = client.chat("Hello! How are you?");
        System.out.println(response);
    }
}
```

### 2. Using Different Providers

```java
// OpenAI (cloud)
LLMClient openai = LLMClientFactory.openAI("gpt-4o", System.getenv("OPENAI_API_KEY"));

// Claude (cloud)
LLMClient claude = LLMClientFactory.claude("claude-sonnet-4-20250514", System.getenv("ANTHROPIC_API_KEY"));

// DeepSeek (cloud)
LLMClient deepseek = LLMClientFactory.deepSeek("deepseek-chat", System.getenv("DEEPSEEK_API_KEY"));

// LM Studio (local)
LLMClient lmStudio = LLMClientFactory.lmStudio("local-model");
```

### 3. With System Prompt

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

String response = client.chat("Explain recursion", Map.of(
    "system", "You are a computer science professor. Be technical but clear."
));

System.out.println(response);
```

### 4. Streaming Response

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

System.out.print("Response: ");
client.streamChat("Write a haiku about coding", chunk -> {
    System.out.print(chunk);
});
System.out.println();
```

### 5. Function Calling (Tools)

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// Register a calculator tool
client.registerTool(
    "calculate",
    "Evaluate mathematical expression",
    args -> {
        String expr = (String) args.get("expression");
        try {
            return new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript").eval(expr);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    },
    Map.of("expression", new ToolRegistry.ParamInfo(
        "expression", "Math expression like 2+2*3", true, String.class))
);

// The AI will automatically call this tool when needed
String response = client.chat("What is 25 * 4 + 10?");
System.out.println(response);
```

### 6. Multi-Modal (Images)

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

Message imageMessage = Message.user()
    .text("What is in this image?")
    .image("https://example.com/image.jpg")
    .build();

String response = client.chat(imageMessage);
System.out.println(response);
```

### 7. Conversation History

```java
LLMClient client = LLMClientFactory.ollama("gemma4:latest");

// First message
client.chat("My favorite color is blue");

// Second message - AI remembers context
String response = client.chat("What's my favorite color?");

// Check history
ConversationHistory history = client.getHistory();
System.out.println("Total messages: " + history.size());

// Clear history for new conversation
client.clearHistory();
```

---

## Advanced Usage

### Custom Client Configuration

```java
ClientConfig config = ClientConfig.of(Provider.OLLAMA)
    .baseUrl("http://192.168.1.100:11434")
    .model("llama3.2")
    .temperature(0.7)
    .maxTokens
    .stream(true)
    .timeout(120.0);

LLMClient client = LLMClient.create(config);
```

### Building Custom Requests

```java
LLMRequest request = LLMRequest.builder()
    .model("gemma4:latest")
    .system("You are a helpful assistant")
    .user("What is the capital of France?")
    .temperature(0.5)
    .maxTokens(200)
    .build();

LLMResponse response = adapter.chat(request);
System.out.println(response.getContent());
```

### Creating a Tool Service Class

```java
import com.simpletoolsindia.llm.framework.tool.*;

@OllamaTool(name = "weather", description = "Get weather for a city")
public class WeatherService {
    
    @ToolParam(name = "city", description = "City name")
    public String getWeather(String city) {
        // Your implementation
        return "Sunny, 72°F in " + city;
    }
}

// Register the service
client.registerTool(new WeatherService());
```

---

## Environment Variables

Create a `.env` file or export these variables:

```bash
# Cloud Provider API Keys
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export DEEPSEEK_API_KEY=sk-...
export NVIDIA_API_KEY=nv-...
export OPENROUTER_API_KEY=sk-...
export MISTRAL_API_KEY=...
export GROQ_API_KEY=...
```

---

## API Reference

### LLMClientFactory

| Method | Description |
|--------|-------------|
| `ollama(model)` | Create Ollama client |
| `openAI(model, apiKey)` | Create OpenAI client |
| `deepSeek(model, apiKey)` | Create DeepSeek client |
| `claude(model, apiKey)` | Create Claude client |
| `nvidia(model, apiKey)` | Create NVIDIA client |
| `openRouter(model, apiKey)` | Create OpenRouter client |
| `lmStudio(model)` | Create LM Studio client |
| `vllm(model)` | Create vLLM client |
| `jan(model)` | Create Jan client |
| `mistral(model, apiKey)` | Create Mistral client |
| `groq(model, apiKey)` | Create Groq client |
| `create(provider, baseUrl, model, apiKey)` | Generic factory |

### LLMClient Methods

| Method | Description |
|--------|-------------|
| `chat(String message)` | Send message, get text response |
| `chat(String msg, Map options)` | Send with options (system, temperature) |
| `chat(Message message)` | Send structured message |
| `streamChat(String msg, Consumer onChunk)` | Streaming response |
| `registerTool(...)` | Register function-calling tool |
| `getHistory()` | Get conversation history |
| `clearHistory()` | Clear all messages |

### Message Factory

```java
Message.ofSystem("You are helpful");
Message.ofUser("Hello");
Message.ofAssistant("Hi there");
Message.ofTool("Tool result");

// With builder
Message.user().text("Hello").image("url").build();
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        LLMClientFactory                          │
│   ollama() • openAI() • claude() • deepSeek() • ...             │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                          LLMClient                               │
│   chat() • streamChat() • registerTool() • getHistory()          │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                      ProviderAdapter                             │
│                     (Strategy Pattern)                           │
├────────────┬────────────┬────────────┬────────────┬──────────────┤
│OllamaAdapter│OpenAIAdapter│ClaudeAdapter│GenericAdapter│    ...    │
└────────────┴────────────┴────────────┴────────────┴──────────────┘
                               │
                    ┌──────────┴──────────┐
                    │     HttpClient      │
                    │  (Java 11+ native)  │
                    └─────────────────────┘
```

### Design Patterns

- **Factory Pattern** - `LLMClientFactory` for client creation
- **Strategy Pattern** - `ProviderAdapter` for different API formats
- **Adapter Pattern** - Translate standard ↔ provider-specific formats
- **Builder Pattern** - `LLMRequest.builder()`, `Tool.builder()`

---

## Project Structure

```
src/main/java/com/simpletoolsindia/llm/framework/
├── LLMFramework.java              # Main entry point
│
├── client/
│   ├── LLMClient.java            # Unified client interface
│   └── LLMClientFactory.java     # Factory for creating clients
│
├── config/
│   ├── Provider.java             # Enum: OLLAMA, OPENAI, CLAUDE, etc.
│   └── ClientConfig.java         # Client configuration builder
│
├── model/
│   ├── Message.java             # Message (text, image, tool calls)
│   ├── LLMRequest.java          # Request model (builder pattern)
│   ├── LLMResponse.java         # Response model (content, usage)
│   ├── Tool.java                 # Tool definition (builder pattern)
│   └── ToolCall.java             # Function call from AI
│
├── adapter/
│   ├── ProviderAdapter.java     # Adapter interface
│   ├── OpenAIAdapter.java       # OpenAI-compatible providers
│   ├── OllamaAdapter.java       # Ollama native format
│   └── ClaudeAdapter.java       # Anthropic Claude API
│
├── tool/
│   ├── ToolRegistry.java        # Tool registration & execution
│   ├── OllamaTool.java          # @OllamaTool annotation
│   └── ToolParam.java           # @ToolParam annotation
│
└── history/
    └── ConversationHistory.java  # Message history management
```

---

## Example Projects

### 1. Simple Chat Bot

```java
public class ChatBot {
    public static void main(String[] args) {
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");
        
        System.out.println("Chat Bot (type 'exit' to quit)");
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("\nYou: ");
            String input = scanner.nextLine();
            
            if ("exit".equalsIgnoreCase(input)) break;
            
            String response = client.chat(input);
            System.out.println("Bot: " + response);
        }
    }
}
```

### 2. AI Assistant with Tools

```java
public class AssistantWithTools {
    public static void main(String[] args) {
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");
        
        // Register file system tool
        client.registerTool(
            "read_file",
            "Read content of a file",
            args -> {
                String path = (String) args.get("path");
                return java.nio.file.Files.readString(java.nio.file.Paths.get(path));
            },
            Map.of("path", new ToolRegistry.ParamInfo(
                "path", "Full file path", true, String.class))
        );
        
        // Register calculator
        client.registerTool(
            "calculate",
            "Evaluate math expression",
            args -> {
                String expr = (String) args.get("expression");
                return new javax.script.ScriptEngineManager()
                    .getEngineByName("JavaScript").eval(expr);
            },
            Map.of("expression", new ToolRegistry.ParamInfo(
                "expression", "Math expression", true, String.class))
        );
        
        // Ask AI to use tools
        String response = client.chat(
            "Read the file at /tmp/example.txt and calculate 100 / 4"
        );
        System.out.println(response);
    }
}
```

### 3. RAG (Retrieval Augmented Generation)

```java
public class RagExample {
    public static void main(String[] args) throws Exception {
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");
        
        // Add documents to knowledge base (simplified)
        String context = """
            Java was created by James Gosling in 1995.
            Python was created by Guido van Rossum in 1991.
            JavaScript was created by Brendan Eich in 1995.
            """;
        
        // Include context in system prompt
        String response = client.chat(
            "Who created Java?",
            Map.of("system", "Use this context to answer: " + context)
        );
        
        System.out.println(response);
    }
}
```

---

## Troubleshooting

### Ollama not running?

```bash
# Start Ollama
ollama serve

# Pull a model
ollama pull gemma4:latest
```

### API Key not set?

```bash
# Linux/Mac
export OPENAI_API_KEY=sk-...

# Windows
set OPENAI_API_KEY=sk-...
```

### Connection timeout?

```java
// Increase timeout
ClientConfig config = ClientConfig.of(Provider.OLLAMA)
    .timeout(300.0); // 5 minutes
```

---

## Dependencies

```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'org.slf4j:slf4j-api:2.0.16'
    implementation 'javax.inject:javax.inject:1'
}
```

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Links

- [GitHub Repository](https://github.com/simpletoolsindia/llm-client-framework)
- [Report Issues](https://github.com/simpletoolsindia/llm-client-framework/issues)
- [Ollama](https://ollama.com/)
- [OpenAI API](https://platform.openai.com/)
- [Anthropic Claude](https://docs.anthropic.com/)

---

**Built with ❤️ for the Java developer community**