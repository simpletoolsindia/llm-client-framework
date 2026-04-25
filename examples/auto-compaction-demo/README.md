# Auto Compaction Demo

Standalone Gradle example that shows how to use `LLMClient` with:

- automatic context window detection
- context usage visibility
- rolling conversation compaction when usage gets too high

## Run

1. Install and start Ollama
2. Pull a model such as `gemma4:latest`
3. Run:

```bash
./gradlew run
```

The demo prints:

- each model response
- current context usage
- the latest compacted summary stored by the framework
