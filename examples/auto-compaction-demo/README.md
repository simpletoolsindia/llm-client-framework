# Framework Showcase Project

Standalone Gradle example project that demonstrates the main framework features in separate runnable examples.

## Included examples

- `basic-chat`
  Simple local chat with Ollama
- `context-window`
  Context usage, projected usage, and manual context-window override
- `auto-compaction`
  Rolling summary compaction when context usage gets too high
- `verbose-logging`
  Richer debug/info/error diagnostics for framework behavior
- `tool-calling`
  Lambda-based tool registration
- `annotation-tools`
  Annotation-based tool registration with `@LLMTool`
- `async-streaming`
  Async chat and streaming output

## Prerequisites

1. Install and start Ollama
2. Pull a model such as `gemma4:latest`
3. Optionally set:

```bash
export OLLAMA_MODEL=gemma4:latest
```

## List available demos

```bash
./gradlew run --args="list"
```

## Run a specific demo

```bash
./gradlew run --args="basic-chat"
./gradlew run --args="context-window"
./gradlew run --args="auto-compaction"
./gradlew run --args="verbose-logging"
./gradlew run --args="tool-calling"
./gradlew run --args="annotation-tools"
./gradlew run --args="async-streaming"
```

## What users can learn here

- how to create an `LLMClient`
- how to call models locally through Ollama
- how to inspect current and projected context usage
- how automatic compaction behaves in long conversations
- how to enable verbose framework logs for debugging
- how to register tools with lambdas and annotations
- how to use async and streaming APIs
