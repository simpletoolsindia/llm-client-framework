package in.simpletools.llm.framework.client;

import in.simpletools.llm.framework.config.*;
import in.simpletools.llm.framework.adapter.*;
import in.simpletools.llm.framework.model.*;
import in.simpletools.llm.framework.tool.*;
import in.simpletools.llm.framework.history.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.Duration;

/**
 * High-performance functional LLM Client with async support and thread-safe tool execution.
 * Design Patterns: Factory, Builder, Strategy, Chain of Responsibility
 */
public class LLMClient {
    private final ProviderAdapter adapter;
    private final ClientConfig config;
    private final ConversationHistory history;
    private final ToolRegistry toolRegistry;
    private final List<Tool> tools;
    private final RetryConfig retryConfig;
    private final ExecutorService executor;

    // ========== Retry Configuration Record ==========
    public record RetryConfig(
        int maxAttempts,
        Duration initialDelay,
        double backoffMultiplier,
        Duration maxDelay
    ) {
        public static RetryConfig defaults() {
            return new RetryConfig(3, Duration.ofMillis(500), 2.0, Duration.ofSeconds(10));
        }
    }

    // ========== Builder ==========
    public static class Builder {
        private ClientConfig config;
        private ConversationHistory history = new ConversationHistory();
        private ToolRegistry toolRegistry = new ToolRegistry();
        private List<Tool> tools = new ArrayList<>();
        private RetryConfig retryConfig = RetryConfig.defaults();
        private ExecutorService executor = Executors.newCachedThreadPool();
        private ProviderAdapter adapter;

        public Builder config(ClientConfig config) { this.config = config; return this; }
        public Builder history(ConversationHistory history) { this.history = history; return this; }
        public Builder tools(List<Tool> tools) { this.tools = tools; return this; }
        public Builder retry(RetryConfig retryConfig) { this.retryConfig = retryConfig; return this; }
        public Builder executor(ExecutorService executor) { this.executor = executor; return this; }
        public Builder adapter(ProviderAdapter adapter) { this.adapter = adapter; return this; }

        public LLMClient build() {
            if (adapter == null && config != null) {
                adapter = createAdapter(config);
            }
            return new LLMClient(adapter, config, history, toolRegistry, tools, retryConfig, executor);
        }
    }

    public static Builder builder() { return new Builder(); }

    // ========== Private Constructor ==========
    private LLMClient(ProviderAdapter adapter, ClientConfig config, ConversationHistory history,
                      ToolRegistry toolRegistry, List<Tool> tools, RetryConfig retryConfig, ExecutorService executor) {
        this.adapter = adapter;
        this.config = config;
        this.history = history;
        this.toolRegistry = toolRegistry;
        this.tools = new ArrayList<>(tools);
        this.retryConfig = retryConfig;
        this.executor = executor;
    }

    // ========== Synchronous Chat ==========
    public String chat(String message) {
        return chat(message, Map.of());
    }

    public String chat(String message, Map<String, String> options) {
        try {
            return chatAsync(message, options).get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public String chat(Message message) {
        try {
            return chatAsync(message).get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    // ========== Async Chat (CompletableFuture) ==========
    public CompletableFuture<String> chatAsync(String message) {
        return chatAsync(message, Map.of());
    }

    public CompletableFuture<String> chatAsync(String message, Map<String, String> options) {
        return CompletableFuture.supplyAsync(() -> {
            history.addUser(message);
            return processMessage(message, options);
        }, executor);
    }

    public CompletableFuture<String> chatAsync(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            history.add(message);
            return processMessageObject(message);
        }, executor);
    }

    // ========== Streaming Chat ==========
    public void streamChat(String message, Consumer<String> onToken) {
        streamChat(message, onToken, e -> System.err.println("Stream error: " + e));
    }

    public void streamChat(String message, Consumer<String> onToken, Consumer<String> onError) {
        CompletableFuture.runAsync(() -> {
            try {
                history.addUser(message);
                LLMRequest request = buildRequest(message, Map.of());
                adapter.streamChat(request, onToken);
            } catch (Exception e) {
                onError.accept(e.getMessage());
            }
        }, executor);
    }

    // ========== Tool Registration ==========
    public LLMClient registerTool(String name, String description,
                                   Function<Map<String, Object>, Object> handler,
                                   Map<String, ToolRegistry.ParamInfo> params) {
        toolRegistry.register(name, description, handler, params);
        tools.add(toolFromInfo(toolRegistry.get(name)));
        return this;
    }

    public LLMClient registerTool(Object service) {
        toolRegistry.register(service);
        toolRegistry.getAllTools().forEach(ti -> tools.add(toolFromInfo(ti)));
        return this;
    }

    public LLMClient withTools(List<Tool> tools) {
        this.tools.clear();
        this.tools.addAll(tools);
        return this;
    }

    public LLMClient withRetry(RetryConfig config) {
        return new LLMClient(adapter, this.config, history, toolRegistry, tools, config, executor);
    }

    // ========== History Management ==========
    public ConversationHistory getHistory() { return history; }
    public LLMClient clearHistory() { history.clear(); return this; }
    public LLMClient clearLastN(int n) { history.clearLastN(n); return this; }

    // ========== Core Processing ==========
    private String processMessage(String message, Map<String, String> options) {
        LLMRequest request = buildRequest(message, options);
        LLMResponse response = adapter.chat(request);

        if (response.hasToolCalls()) {
            return handleToolCallsAsync(response.getToolCalls()).join();
        }
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        return reply;
    }

    private String processMessageObject(Message message) {
        LLMRequest request = LLMRequest.builder()
            .model(config.getModel())
            .messages(new ArrayList<>(history.getMessages()))
            .addMessage(message)
            .tools(tools.isEmpty() ? null : tools)
            .build();

        LLMResponse response = adapter.chat(request);

        if (response.hasToolCalls()) {
            return handleToolCallsAsync(response.getToolCalls()).join();
        }
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        return reply;
    }

    // ========== Thread-Safe Tool Execution with Retry ==========
    private CompletableFuture<String> handleToolCallsAsync(List<ToolCall> calls) {
        List<CompletableFuture<String>> futures = calls.stream()
            .map(this::executeToolWithRetryAsync)
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                StringBuilder results = new StringBuilder();
                futures.forEach(f -> {
                    try { results.append(f.get()).append("\n"); }
                    catch (Exception e) { results.append("Error: ").append(e.getMessage()).append("\n"); }
                });
                return results.toString().trim();
            })
            .thenCompose(results -> {
                // Add tool results to history
                calls.forEach(call -> {
                    Message toolMsg = Message.ofTool(results);
                    toolMsg.setName(call.getFunction().getName());
                    history.add(toolMsg);
                });
                // Continue conversation
                return continueConversation();
            });
    }

    private CompletableFuture<String> executeToolWithRetryAsync(ToolCall call) {
        return CompletableFuture.supplyAsync(() -> {
            String toolName = call.getFunction().getName();
            Map<String, Object> args = call.getFunction().getArguments();

            int attempt = 0;
            Duration delay = retryConfig.initialDelay();

            while (attempt < retryConfig.maxAttempts()) {
                try {
                    ToolRegistry.ToolInfo info = toolRegistry.get(toolName);
                    if (info == null) {
                        return "{\"error\": \"Tool not found: " + toolName + "\"}";
                    }
                    Object result = info.invoke(args);
                    return new com.google.gson.Gson().toJson(result);
                } catch (Exception e) {
                    attempt++;
                    if (attempt >= retryConfig.maxAttempts()) {
                        return "{\"error\": \"Tool execution failed after " + retryConfig.maxAttempts() + " attempts: " + e.getMessage() + "\"}";
                    }
                    try { Thread.sleep(delay.toMillis()); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "{\"error\": \"Tool execution interrupted: " + e.getMessage() + "\"}";
                    }
                    delay = Duration.ofMillis(Math.min(
                        (long)(delay.toMillis() * retryConfig.backoffMultiplier()),
                        retryConfig.maxDelay().toMillis()
                    ));
                }
            }
            return "{\"error\": \"Tool execution failed\"}";
        }, executor);
    }

    private CompletableFuture<String> continueConversation() {
        return CompletableFuture.supplyAsync(() -> {
            LLMRequest request = LLMRequest.builder()
                .model(config.getModel())
                .messages(history.getMessages())
                .tools(tools.isEmpty() ? null : tools)
                .build();

            LLMResponse response = adapter.chat(request);
            String reply = response.getContentOrEmpty();
            history.addAssistant(reply);
            return reply;
        }, executor);
    }

    // ========== Request Building ==========
    private LLMRequest buildRequest(String message, Map<String, String> options) {
        return LLMRequest.builder()
            .model(config.getModel())
            .stream(config.isStream())
            .messages(new ArrayList<>(history.getMessages()))
            .addMessage(Message.ofUser(message))
            .system(options.get("system"))
            .temperature(options.get("temperature") != null ? Double.parseDouble(options.get("temperature")) : null)
            .tools(tools.isEmpty() ? null : tools)
            .build();
    }

    // ========== Tool Conversion ==========
    private Tool toolFromInfo(ToolRegistry.ToolInfo info) {
        Tool tool = new Tool();
        Tool.Function fn = new Tool.Function();
        fn.setName(info.getName());
        fn.setDescription(info.getDescription());
        fn.setParameters(new HashMap<>());
        info.getParams().forEach((k, v) -> {
            Tool.Function.Param p = new Tool.Function.Param();
            Class<?> t = v.getType();
            p.setType(switch (t.getName()) {
                case "java.lang.String" -> "string";
                case "int", "java.lang.Integer", "long", "java.lang.Long" -> "integer";
                case "boolean", "java.lang.Boolean" -> "boolean";
                case "double", "java.lang.Double", "float", "java.lang.Float" -> "number";
                default -> "string";
            });
            p.setDescription(v.getDescription());
            fn.getParameters().put(k, p);
        });
        tool.setFunction(fn);
        return tool;
    }

    // ========== Static Factory Methods ==========
    public static LLMClient create(ClientConfig config) {
        return builder().config(config).build();
    }

    public static LLMClient ollama(String model) {
        return create(ClientConfig.of(Provider.OLLAMA).model(model));
    }

    public static LLMClient ollama(String baseUrl, String model) {
        return create(ClientConfig.of(Provider.OLLAMA).baseUrl(baseUrl).model(model));
    }

    public static LLMClient openAI(String model, String apiKey) {
        return create(ClientConfig.of(Provider.OPENAI).model(model).apiKey(apiKey));
    }

    public static LLMClient deepSeek(String model, String apiKey) {
        return create(ClientConfig.of(Provider.DEEPSEEK).model(model).apiKey(apiKey));
    }

    public static LLMClient claude(String model, String apiKey) {
        return create(ClientConfig.of(Provider.ANTHROPIC).model(model).apiKey(apiKey));
    }

    public static LLMClient nvidia(String model, String apiKey) {
        return create(ClientConfig.of(Provider.NVIDIA).model(model).apiKey(apiKey));
    }

    public static LLMClient openRouter(String model, String apiKey) {
        return create(ClientConfig.of(Provider.OPENROUTER).model(model).apiKey(apiKey));
    }

    public static LLMClient lmStudio(String model) {
        return create(ClientConfig.of(Provider.LM_STUDIO).model(model));
    }

    public static LLMClient vllm(String model) {
        return create(ClientConfig.of(Provider.VLLM).model(model));
    }

    public static LLMClient jan(String model) {
        return create(ClientConfig.of(Provider.JAN).model(model));
    }

    public static LLMClient groq(String model, String apiKey) {
        return create(ClientConfig.of(Provider.GROQ).model(model).apiKey(apiKey));
    }

    public static LLMClient mistral(String model, String apiKey) {
        return create(ClientConfig.of(Provider.MISTRAL).model(model).apiKey(apiKey));
    }

    private static ProviderAdapter createAdapter(ClientConfig config) {
        return switch (config.getProvider()) {
            case OLLAMA -> new OllamaAdapter(config.getBaseUrl(), config.getModel());
            case ANTHROPIC -> new ClaudeAdapter(config.getBaseUrl(), config.getModel(), config.getApiKey());
            default -> new OpenAIAdapter(config);
        };
    }
}
