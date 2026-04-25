package in.simpletools.llm.framework.client;

import in.simpletools.llm.framework.config.*;
import in.simpletools.llm.framework.adapter.*;
import in.simpletools.llm.framework.model.*;
import in.simpletools.llm.framework.tool.*;
import in.simpletools.llm.framework.history.*;
import in.simpletools.llm.framework.utils.SimpleLogger;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.Duration;

/**
 * High-performance functional LLM Client with async support, tool execution, and token tracking.
 *
 * <p><b>Design Patterns:</b> Factory, Builder, Strategy, Chain of Responsibility
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Simple fluent tool registration: {@code client.tool("name", desc, (args) -> result)}</li>
 *   <li>Annotation-based auto-registration: {@code client.registerTools(myObject)}</li>
 *   <li>Token tracking: {@code client.getContextInfo()}</li>
 *   <li>Redis-backed history: {@code client.withRedisHistory("session-123")}</li>
 *   <li>Built-in system tools: {@code client.withSystemTools()}</li>
 *   <li>Configurable logging</li>
 * </ul>
 *
 * <pre>
 * {@code
 * // Simple chat
 * LLMClient client = LLMClient.ollama("gemma4:latest");
 * String reply = client.chat("Hello!");
 *
 * // With tools (easy fluent API)
 * client.tool("calculate", "Evaluate math", args -> eval(args.get("expr").toString()))
 *       .tool("search", "Search the web", args -> webSearch(args.get("query").toString()));
 *
 * // With token tracking
 * ContextInfo info = client.getContextInfo();
 * System.out.println(info.summary());
 *
 * // With Redis history
 * client.withRedisHistory("user-session-1");
 *
 * // With system tools
 * client.withSystemTools();
 *
 * String reply = client.chat("Read README.md and tell me about it");
 * }
 * </pre>
 */
public class LLMClient {
    private final ProviderAdapter adapter;
    private final ClientConfig config;
    private final SimpleLogger logger;
    private ConversationHistory history;
    private ToolRegistry toolRegistry;
    private List<Tool> tools;
    private RetryConfig retryConfig;
    private final ExecutorService executor;
    private TokenTracker tokenTracker;

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

        public static RetryConfig none() {
            return new RetryConfig(0, Duration.ZERO, 1.0, Duration.ZERO);
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
        private TokenTracker tokenTracker;
        private SimpleLogger logger = SimpleLogger.get("LLMClient");

        public Builder config(ClientConfig config) { this.config = config; return this; }
        public Builder history(ConversationHistory h) { this.history = h; return this; }
        public Builder history(ConversationHistoryStore h) {
            this.history = new RedisHistoryAdapter(h); return this;
        }
        public Builder tools(List<Tool> t) { this.tools = t; return this; }
        public Builder retry(RetryConfig r) { this.retryConfig = r; return this; }
        public Builder executor(ExecutorService e) { this.executor = e; return this; }
        public Builder adapter(ProviderAdapter a) { this.adapter = a; return this; }
        public Builder tokenTracker(TokenTracker t) { this.tokenTracker = t; return this; }
        public Builder logger(SimpleLogger l) { this.logger = l; return this; }
        public Builder loggerLevel(SimpleLogger.Level level) {
            this.logger.setLevel(level); return this;
        }

        public LLMClient build() {
            if (adapter == null && config != null) adapter = createAdapter(config);
            return new LLMClient(adapter, config, history, toolRegistry, tools, retryConfig, executor, logger, tokenTracker);
        }
    }

    public static Builder builder() { return new Builder(); }

    // ========== Private Constructor ==========
    private LLMClient(ProviderAdapter adapter, ClientConfig config, ConversationHistory history,
                      ToolRegistry toolRegistry, List<Tool> tools, RetryConfig retryConfig,
                      ExecutorService executor, SimpleLogger logger, TokenTracker tokenTracker) {
        this.adapter = adapter;
        this.config = config;
        this.history = history;
        this.toolRegistry = toolRegistry;
        this.tools = new ArrayList<>(tools);
        this.retryConfig = retryConfig;
        this.executor = executor;
        this.logger = logger;
        this.tokenTracker = tokenTracker != null ? tokenTracker : new TokenTracker(config.getModel(), TokenTracker.getLimitForModel(config.getModel()));
    }

    // ========== Easy Tool Registration (Simplified API) ==========

    /**
     * Register a tool with a single lambda. Simplest way to add tools.
     *
     * <pre>
     * {@code
     * // No params needed - just a handler
     * client.tool("ping", "Check if service is up", () -> "pong");
     *
     * // With params - receive args Map
     * client.tool("calculate", "Evaluate math expression",
     *     args -> eval(args.get("expr").toString()));
     * }
     * </pre>
     */
    public LLMClient tool(String name, String description,
                          Function<Map<String, Object>, Object> handler) {
        return tool(name, description, handler, Map.of());
    }

    /**
     * Register a tool with params.
     */
    public LLMClient tool(String name, String description,
                          Function<Map<String, Object>, Object> handler,
                          Map<String, ToolRegistry.ParamInfo> params) {
        return tool(name, description, handler, params, retryConfig.maxAttempts(),
            retryConfig.initialDelay().toMillis(), retryConfig.backoffMultiplier(),
            retryConfig.maxDelay().toMillis());
    }

    /**
     * Register a tool with full retry configuration.
     */
    public LLMClient tool(String name, String description,
                          Function<Map<String, Object>, Object> handler,
                          Map<String, ToolRegistry.ParamInfo> params,
                          int maxRetries, long retryDelayMs,
                          double backoffMultiplier, long maxRetryDelayMs) {
        toolRegistry.register(name, description, handler, params, maxRetries, retryDelayMs, backoffMultiplier, maxRetryDelayMs);
        tools.add(toolFromInfo(toolRegistry.get(name)));
        logger.debug("Registered tool: {}", name);
        return this;
    }

    /**
     * Register a tool with no parameters.
     */
    public LLMClient tool(String name, String description, Runnable runnable) {
        return tool(name, description, args -> { runnable.run(); return "Done"; });
    }

    /**
     * Register ALL methods annotated with {@link LLMTool} from a service object.
     * Auto-discovers tools from the object's class.
     *
     * <pre>
     * {@code
     * MyTools myTools = new MyTools();
     * client.registerTools(myTools);  // registers all @LLMTool methods
     * }
     * </pre>
     */
    public LLMClient registerTools(Object service) {
        int before = toolRegistry.getToolCount();
        toolRegistry.registerAll(service);
        toolRegistry.getAllTools().forEach(ti -> {
            if (tools.stream().noneMatch(t -> t.getFunction().getName().equals(ti.getName()))) {
                tools.add(toolFromInfo(ti));
            }
        });
        logger.info("Auto-registered {} tools from {}", toolRegistry.getToolCount() - before, service.getClass().getSimpleName());
        return this;
    }

    /**
     * Register built-in system tools (file, web, bash).
     * Convenience method combining all system tool categories.
     */
    public LLMClient withSystemTools() {
        return withSystemTools("all");
    }

    /**
     * Register specific category of system tools.
     * @param category "all", "file", "web", or "shell"
     */
    public LLMClient withSystemTools(String category) {
        switch (category.toLowerCase()) {
            case "file" -> in.simpletools.llm.framework.tools.SystemTools.registerFileTools(toolRegistry);
            case "web" -> in.simpletools.llm.framework.tools.SystemTools.registerWebTools(toolRegistry);
            case "shell" -> in.simpletools.llm.framework.tools.SystemTools.registerShellTools(toolRegistry);
            default -> in.simpletools.llm.framework.tools.SystemTools.registerAll(toolRegistry);
        }
        toolRegistry.getAllTools().forEach(ti -> {
            if (tools.stream().noneMatch(t -> t.getFunction().getName().equals(ti.getName()))) {
                tools.add(toolFromInfo(ti));
            }
        });
        logger.info("Registered {} system tools ({})", toolRegistry.getToolCount(), category);
        return this;
    }

    /**
     * Register HTTP client tools for calling external REST APIs.
     * Adds: http_get, http_post, http_put, http_patch, http_delete
     *
     * <pre>
     * {@code
     * client.withHttpTools();
     * // Now the LLM can call external APIs like:
     * // "GET https://api.example.com/users"
     * // "POST https://api.example.com/users with body {"name":"John"}"
     * }
     * </pre>
     */
    public LLMClient withHttpTools() {
        in.simpletools.llm.framework.tools.HttpTools.registerAll(toolRegistry);
        toolRegistry.getAllTools().stream()
            .filter(ti -> ti.getName().startsWith("http_"))
            .forEach(ti -> {
                if (tools.stream().noneMatch(t -> t.getFunction().getName().equals(ti.getName()))) {
                    tools.add(toolFromInfo(ti));
                }
            });
        int httpCount = (int) toolRegistry.getAllTools().stream().filter(ti -> ti.getName().startsWith("http_")).count();
        logger.info("Registered {} HTTP tools", httpCount);
        return this;
    }

    // ========== Synchronous Chat ==========
    public String chat(String message) { return chat(message, Map.of()); }

    public String chat(String message, Map<String, String> options) {
        try {
            return chatAsync(message, options).get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("Chat failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    public String chat(Message message) {
        try { return chatAsync(message).get(5, TimeUnit.MINUTES); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // ========== Async Chat ==========
    public CompletableFuture<String> chatAsync(String message) { return chatAsync(message, Map.of()); }

    public CompletableFuture<String> chatAsync(String message, Map<String, String> options) {
        return CompletableFuture.supplyAsync(() -> {
            history.addUser(message);
            tokenTracker.recordUser(message);
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
        streamChat(message, onToken, e -> logger.error("Stream error: {}", e));
    }

    public void streamChat(String message, Consumer<String> onToken, Consumer<String> onError) {
        StringBuilder response = new StringBuilder();
        CompletableFuture.runAsync(() -> {
            try {
                history.addUser(message);
                LLMRequest request = buildRequest(message, Map.of());
                adapter.streamChat(request, token -> {
                    response.append(token);
                    onToken.accept(token);
                });
                // Add assistant response to history after streaming completes
                String fullResponse = response.toString();
                history.addAssistant(fullResponse);
                tokenTracker.recordAssistant(fullResponse);
            } catch (Exception e) {
                onError.accept(e.getMessage());
                logger.error("Stream failed: {}", e.getMessage());
            }
        }, executor);
    }

    // ========== History Management ==========
    public ConversationHistory getHistory() { return history; }
    public LLMClient clearHistory() { history.clear(); tokenTracker.reset(); return this; }
    public LLMClient clearLastN(int n) { history.clearLastN(n); return this; }

    /**
     * Switch to Redis-backed history for persistent conversations.
     * Uses in-memory store if Jedis is not on classpath.
     */
    public LLMClient withRedisHistory(String conversationId) {
        return withRedisHistory(conversationId, "localhost", 6379);
    }

    public LLMClient withRedisHistory(String conversationId, String host, int port) {
        try {
            RedisHistory redis = RedisHistory.withJedis(host, port, conversationId);
            if (!redis.isAvailable()) {
                logger.warn("Redis unavailable at {}:{}, using in-memory fallback", host, port);
                redis = RedisHistory.inMemory(conversationId);
            }
            this.history = new RedisHistoryAdapter(redis);
            logger.info("Using Redis history for conversation: {}", conversationId);
        } catch (Exception e) {
            logger.warn("Failed to connect to Redis, using in-memory history: {}", e.getMessage());
            this.history = new RedisHistoryAdapter(RedisHistory.inMemory(conversationId));
        }
        return this;
    }

    /** Use in-memory history (default). */
    public LLMClient withMemoryHistory() {
        this.history = new ConversationHistory();
        return this;
    }

    // ========== Token Tracking ==========

    /** Get current context window usage info. */
    public TokenTracker.ContextInfo getContextInfo() { return tokenTracker.getContextInfo(); }

    /** Get the token tracker for detailed usage. */
    public TokenTracker getTokenTracker() { return tokenTracker; }

    /** Print context info to stdout. */
    public void printContextInfo() {
        System.out.println(TokenTracker.formatContextInfo(tokenTracker.getContextInfo()));
    }

    // ========== Configuration Methods ==========
    public LLMClient withTools(List<Tool> tools) {
        this.tools.clear(); this.tools.addAll(tools); return this;
    }
    public LLMClient withRetry(RetryConfig config) {
        return new LLMClient(adapter, this.config, this.history, toolRegistry, tools, config, executor, logger, tokenTracker);
    }
    public LLMClient withRetry(int maxAttempts) {
        return withRetry(new RetryConfig(maxAttempts, Duration.ofMillis(500), 2.0, Duration.ofSeconds(10)));
    }
    public LLMClient setLogLevel(SimpleLogger.Level level) {
        logger.setLevel(level); return this;
    }
    public SimpleLogger getLogger() { return logger; }

    // ========== Core Processing ==========
    private String processMessage(String message, Map<String, String> options) {
        logger.debug("Processing message: {} chars", message.length());
        LLMRequest request = buildRequest(message, options);
        LLMResponse response = adapter.chat(request);

        if (tokenTracker != null) tokenTracker.updateFromUsage(response);

        if (response.hasToolCalls()) {
            logger.debug("Handling {} tool calls", response.getToolCalls().size());
            return handleToolCallsAsync(response.getToolCalls()).join();
        }
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        tokenTracker.recordAssistant(reply);
        logger.debug("Response: {} chars", reply.length());
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
        tokenTracker.updateFromUsage(response);

        if (response.hasToolCalls()) {
            return handleToolCallsAsync(response.getToolCalls()).join();
        }
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        return reply;
    }

    // ========== Tool Execution with Retry ==========
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
                calls.forEach(call -> {
                    Message toolMsg = Message.ofTool(results);
                    toolMsg.setName(call.getFunction().getName());
                    history.add(toolMsg);
                });
                tokenTracker.recordTool(results);
                return continueConversation();
            });
    }

    private CompletableFuture<String> executeToolWithRetryAsync(ToolCall call) {
        return CompletableFuture.supplyAsync(() -> {
            String toolName = call.getFunction().getName();
            Map<String, Object> args = call.getFunction().getArguments();
            ToolRegistry.ToolInfo info = toolRegistry.get(toolName);

            if (info == null) {
                logger.warn("Tool not found: {}", toolName);
                return "{\"error\": \"Tool not found: " + toolName + "\"}";
            }

            int maxRetries = info.getMaxRetries();
            if (maxRetries <= 0) {
                try {
                    Object result = info.invoke(args);
                    return new com.google.gson.Gson().toJson(result);
                } catch (Exception e) {
                    return "{\"error\": \"" + e.getMessage() + "\"}";
                }
            }

            long delay = 500;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    long start = System.currentTimeMillis();
                    Object result = info.invoke(args);
                    long elapsed = System.currentTimeMillis() - start;
                    logger.debug("Tool '{}' executed in {}ms (attempt {})", toolName, elapsed, attempt + 1);
                    return new com.google.gson.Gson().toJson(result);
                } catch (Exception e) {
                    logger.warn("Tool '{}' failed (attempt {}/{}): {}",
                        toolName, attempt + 1, maxRetries, e.getMessage());
                    if (attempt >= maxRetries - 1) {
                        return "{\"error\": \"Tool execution failed after " + maxRetries + " attempts: " + e.getMessage() + "\"}";
                    }
                    try { Thread.sleep(delay); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "{\"error\": \"Tool execution interrupted\"}";
                    }
                    delay = Math.min((long)(delay * 2.0), 10000);
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
            tokenTracker.updateFromUsage(response);
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
            p.setType(ToolRegistry.ParamInfo.jsonType(v.getType()));
            p.setDescription(v.getDescription());
            fn.getParameters().put(k, p);
        });
        tool.setFunction(fn);
        return tool;
    }

    // ========== Static Factory Methods ==========
    public static LLMClient create(ClientConfig config) { return builder().config(config).build(); }

    public static LLMClient ollama(String model) { return create(ClientConfig.of(Provider.OLLAMA).model(model)); }
    public static LLMClient ollama(String baseUrl, String model) { return create(ClientConfig.of(Provider.OLLAMA).baseUrl(baseUrl).model(model)); }
    public static LLMClient openAI(String model, String apiKey) { return create(ClientConfig.of(Provider.OPENAI).model(model).apiKey(apiKey)); }
    public static LLMClient deepSeek(String model, String apiKey) { return create(ClientConfig.of(Provider.DEEPSEEK).model(model).apiKey(apiKey)); }
    public static LLMClient claude(String model, String apiKey) { return create(ClientConfig.of(Provider.ANTHROPIC).model(model).apiKey(apiKey)); }
    public static LLMClient nvidia(String model, String apiKey) { return create(ClientConfig.of(Provider.NVIDIA).model(model).apiKey(apiKey)); }
    public static LLMClient openRouter(String model, String apiKey) { return create(ClientConfig.of(Provider.OPENROUTER).model(model).apiKey(apiKey)); }
    public static LLMClient lmStudio(String model) { return create(ClientConfig.of(Provider.LM_STUDIO).model(model)); }
    public static LLMClient vllm(String model) { return create(ClientConfig.of(Provider.VLLM).model(model)); }
    public static LLMClient jan(String model) { return create(ClientConfig.of(Provider.JAN).model(model)); }
    public static LLMClient groq(String model, String apiKey) { return create(ClientConfig.of(Provider.GROQ).model(model).apiKey(apiKey)); }
    public static LLMClient mistral(String model, String apiKey) { return create(ClientConfig.of(Provider.MISTRAL).model(model).apiKey(apiKey)); }

    private static ProviderAdapter createAdapter(ClientConfig config) {
        return switch (config.getProvider()) {
            case OLLAMA -> new OllamaAdapter(config.getBaseUrl(), config.getModel());
            case ANTHROPIC -> new ClaudeAdapter(config.getBaseUrl(), config.getModel(), config.getApiKey());
            default -> new OpenAIAdapter(config);
        };
    }

    // ========== Adapters ========
    private static class RedisHistoryAdapter extends ConversationHistory {
        private final ConversationHistoryStore store;
        RedisHistoryAdapter(ConversationHistoryStore store) { this.store = store; }
        @Override public void addUser(String c) { store.addUser(c); }
        @Override public void addAssistant(String c) { store.addAssistant(c); }
        @Override public void addSystem(String c) { store.addSystem(c); }
        @Override public void addTool(String c) { store.addTool(c); }
        @Override public void add(Message m) { store.add(m); }
        @Override public List<Message> getMessages() { return store.getMessages(); }
        @Override public List<Message> getLast(int n) { return store.getLast(n); }
        @Override public void clear() { store.clear(); }
        @Override public int size() { return store.size(); }
    }
}
