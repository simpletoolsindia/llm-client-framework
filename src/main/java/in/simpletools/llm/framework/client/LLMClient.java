package in.simpletools.llm.framework.client;

import in.simpletools.llm.framework.config.*;
import in.simpletools.llm.framework.adapter.*;
import in.simpletools.llm.framework.model.*;
import in.simpletools.llm.framework.tool.*;
import in.simpletools.llm.framework.history.*;
import in.simpletools.llm.framework.utils.SimpleLogger;
import in.simpletools.llm.framework.utils.Retry;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * High-performance functional LLM Client with async support, tool execution, and token tracking.
 */
public class LLMClient implements AutoCloseable {
    private final ProviderAdapter adapter;
    private final ClientConfig config;
    private final SimpleLogger logger;
    private ConversationHistory history;
    private ToolRegistry toolRegistry;
    private List<Tool> tools;
    private Retry.RetryConfig retryConfig;
    private final ExecutorService executor;
    private TokenTracker tokenTracker;
    private Consumer<LLMStatus> statusListener = status -> {};
    private boolean autoCompactEnabled = true;
    private double autoCompactTriggerPercent = 85.0;
    private double autoCompactTargetPercent = 55.0;
    private int compactKeepLastMessages = 6;
    private String compactedContextSummary;
    private boolean compactingHistory;
    private boolean manualContextWindowConfigured;
    private static final int MAX_COMPACTION_ATTEMPTS = 3;
    private static final String COMPACTED_CONTEXT_PREFIX = "Conversation summary for continued context:\n";
    private static final String COMPACTION_SYSTEM_PROMPT = """
        You are compressing a conversation to preserve continuity in a limited context window.
        Extract only durable information that matters for future turns.
        Keep: user goals, important facts, key decisions, unresolved questions, important tool outputs.
        Remove chatter, repetition, and temporary filler.
        Return a compact continuation summary in plain text.
        """;

    // ========== Builder ==========
    /**
     * Builder for advanced client construction.
     *
     * <p>Use the builder when you need a custom history store, custom executor,
     * mock adapter for tests, custom logger, or prebuilt tool schema list.</p>
     */
    public static class Builder {
        private ClientConfig config;
        private ConversationHistory history = new ConversationHistory();
        private ToolRegistry toolRegistry = new ToolRegistry();
        private List<Tool> tools = new ArrayList<>();
        private Retry.RetryConfig retryConfig = Retry.RetryConfig.defaults();
        private ExecutorService executor = Executors.newCachedThreadPool();
        private ProviderAdapter adapter;
        private TokenTracker tokenTracker;
        private SimpleLogger logger = SimpleLogger.get("LLMClient");

        /** @param config provider/model/API configuration @return this builder */
        public Builder config(ClientConfig config) { this.config = config; return this; }
        /** @param h in-memory conversation history @return this builder */
        public Builder history(ConversationHistory h) { this.history = h; return this; }
        /** @param h pluggable conversation history store @return this builder */
        public Builder history(ConversationHistoryStore h) { this.history = new RedisHistoryAdapter(h); return this; }
        /** @param t prebuilt provider-neutral tool schemas @return this builder */
        public Builder tools(List<Tool> t) { this.tools = new ArrayList<>(t); return this; }
        /** @param r retry configuration for registered tools @return this builder */
        public Builder retry(Retry.RetryConfig r) { this.retryConfig = r; return this; }
        /** @param e executor used for async chat and tool execution @return this builder */
        public Builder executor(ExecutorService e) { this.executor = e; return this; }
        /** @param a custom provider adapter, useful for tests or custom gateways @return this builder */
        public Builder adapter(ProviderAdapter a) { this.adapter = a; return this; }
        /** @param t token tracker to use @return this builder */
        public Builder tokenTracker(TokenTracker t) { this.tokenTracker = t; return this; }
        /** @param l logger to use @return this builder */
        public Builder logger(SimpleLogger l) { this.logger = l; return this; }
        /** @param level logger level @return this builder */
        public Builder loggerLevel(SimpleLogger.Level level) { this.logger.setLevel(level); return this; }

        /** @return configured client */
        public LLMClient build() {
            var resolvedAdapter = adapter != null ? adapter : config != null ? createAdapter(config) : null;
            return new LLMClient(resolvedAdapter, config, history, toolRegistry, tools, retryConfig, executor, logger, tokenTracker);
        }
    }

    /** @return builder for advanced client construction */
    public static Builder builder() { return new Builder(); }

    // ========== Private Constructor ==========
    private LLMClient(ProviderAdapter adapter, ClientConfig config, ConversationHistory history,
                      ToolRegistry toolRegistry, List<Tool> tools, Retry.RetryConfig retryConfig,
                      ExecutorService executor, SimpleLogger logger, TokenTracker tokenTracker) {
        this.adapter = adapter;
        this.config = config;
        this.history = history;
        this.toolRegistry = toolRegistry;
        this.tools = new ArrayList<>(tools);
        this.retryConfig = retryConfig;
        this.executor = executor;
        this.logger = logger;
        this.tokenTracker = tokenTracker != null ? tokenTracker : new TokenTracker(config.model(), TokenTracker.detectLimitForModel(config.model()));
    }

    // ========== Easy Tool Registration ==========

    /**
     * Register a function tool that the model can call during chat.
     *
     * <pre>{@code
     * client.tool("weather", "Get weather for a city", args -> {
     *     String city = args.get("city").toString();
     *     return "Sunny in " + city;
     * });
     * }</pre>
     *
     * @param name tool name exposed to the model, for example {@code weather}
     * @param description clear description of when the model should call this tool
     * @param handler function that receives model-provided arguments and returns a result
     * @return this client for fluent chaining
     */
    public LLMClient tool(String name, String description,
                          Function<Map<String, Object>, Object> handler) {
        return tool(name, description, handler, Map.of());
    }

    /**
     * Register a function tool with typed parameter metadata for better tool-calling accuracy.
     *
     * <pre>{@code
     * client.tool(
     *     "calculate",
     *     "Evaluate a math expression",
     *     args -> "42",
     *     Map.of("expression", new ToolRegistry.ParamInfo(
     *         "expression", "Expression to evaluate", true, String.class))
     * );
     * }</pre>
     *
     * @param name tool name exposed to the model
     * @param description clear description of when the model should call this tool
     * @param handler function that receives model-provided arguments and returns a result
     * @param params parameter names, descriptions, required flags, and Java types
     * @return this client for fluent chaining
     */
    public LLMClient tool(String name, String description,
                          Function<Map<String, Object>, Object> handler,
                          Map<String, ToolRegistry.ParamInfo> params) {
        return tool(name, description, handler, params, retryConfig.maxAttempts(),
            retryConfig.initialDelayMs(), retryConfig.backoffMultiplier(),
            retryConfig.maxDelayMs());
    }

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
     * Register a no-argument command-style tool.
     *
     * @param name tool name exposed to the model
     * @param description clear description of when the model should call this tool
     * @param runnable command to run when the tool is called
     * @return this client for fluent chaining
     */
    public LLMClient tool(String name, String description, Runnable runnable) {
        return tool(name, description, args -> { runnable.run(); return "Done"; });
    }

    /**
     * Auto-register methods annotated with {@link LLMTool} or {@link OllamaTool}.
     *
     * <pre>{@code
     * class TravelTools {
     *     @LLMTool(name = "city_tip", description = "Return a short travel tip")
     *     public String cityTip(@ToolParam(name = "city") String city) {
     *         return "Visit early in " + city;
     *     }
     * }
     *
     * client.registerTools(new TravelTools());
     * }</pre>
     *
     * @param service object containing annotated public or package-visible tool methods
     * @return this client for fluent chaining
     */
    public LLMClient registerTools(Object service) {
        int before = toolRegistry.getToolCount();
        toolRegistry.registerAll(service);
        toolRegistry.getAllTools().forEach(ti -> {
            if (tools.stream().noneMatch(t -> t.function().name().equals(ti.name()))) {
                tools.add(toolFromInfo(ti));
            }
        });
        logger.info("Auto-registered {} tools from {}", toolRegistry.getToolCount() - before, service.getClass().getSimpleName());
        return this;
    }

    /**
     * Register all built-in system tools.
     *
     * <p>Includes file tools, directory search tools, web search/fetch tools, and
     * shell command execution. Use carefully when exposing the client to untrusted
     * prompts because these tools can read/write files and run commands.</p>
     *
     * @return this client for fluent chaining
     */
    public LLMClient withSystemTools() { return withSystemTools("all"); }

    /**
     * Register a built-in system tool group.
     *
     * <p>Supported categories:</p>
     * <ul>
     *   <li>{@code "all"}: all built-in system tools</li>
     *   <li>{@code "file"}: file, directory, grep, and metadata tools</li>
     *   <li>{@code "web"}: {@code web_search} and {@code fetch_webpage}</li>
     *   <li>{@code "shell"}: {@code run_bash}</li>
     * </ul>
     *
     * @param category tool group name; unknown values fall back to {@code "all"}
     * @return this client for fluent chaining
     */
    public LLMClient withSystemTools(String category) {
        switch (category.toLowerCase()) {
            case "file" -> in.simpletools.llm.framework.tools.SystemTools.registerFileTools(toolRegistry);
            case "web" -> in.simpletools.llm.framework.tools.SystemTools.registerWebTools(toolRegistry);
            case "shell" -> in.simpletools.llm.framework.tools.SystemTools.registerShellTools(toolRegistry);
            default -> in.simpletools.llm.framework.tools.SystemTools.registerAll(toolRegistry);
        }
        toolRegistry.getAllTools().forEach(ti -> {
            if (tools.stream().noneMatch(t -> t.function().name().equals(ti.name()))) {
                tools.add(toolFromInfo(ti));
            }
        });
        logger.info("Registered {} system tools ({})", toolRegistry.getToolCount(), category);
        return this;
    }

    /**
     * Register built-in HTTP tools for making REST requests from model tool calls.
     *
     * @return this client for fluent chaining
     */
    public LLMClient withHttpTools() {
        in.simpletools.llm.framework.tools.HttpTools.registerAll(toolRegistry);
        toolRegistry.getAllTools().stream()
            .filter(ti -> ti.name().startsWith("http_"))
            .forEach(ti -> {
                if (tools.stream().noneMatch(t -> t.function().name().equals(ti.name()))) {
                    tools.add(toolFromInfo(ti));
                }
            });
        long httpCount = toolRegistry.getAllTools().stream().filter(ti -> ti.name().startsWith("http_")).count();
        logger.info("Registered {} HTTP tools", httpCount);
        return this;
    }

    // ========== Synchronous Chat ==========
    /**
     * Send a user message and return the model reply.
     *
     * @param message user prompt
     * @return assistant reply, or a string beginning with {@code Error:} when the request fails
     */
    public String chat(String message) { return chat(message, Map.of()); }

    /**
     * Send a user message with per-request options.
     *
     * <p>Supported options include {@code system} for a temporary system prompt and
     * {@code temperature} for sampling temperature.</p>
     *
     * @param message user prompt
     * @param options per-request options such as {@code system} and {@code temperature}
     * @return assistant reply, or a string beginning with {@code Error:} when the request fails
     */
    public String chat(String message, Map<String, String> options) {
        try {
            return chatAsync(message, options).get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("Chat failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Send a fully constructed message.
     *
     * @param message user, system, assistant, or tool message to append
     * @return assistant reply, or an {@code Error:} string
     */
    public String chat(Message message) {
        try { return chatAsync(message).get(5, TimeUnit.MINUTES); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    // ========== Async Chat ==========
    /**
     * Send a user message asynchronously.
     *
     * @param message user prompt
     * @return future resolving to assistant reply
     */
    public CompletableFuture<String> chatAsync(String message) { return chatAsync(message, Map.of()); }

    /**
     * Send a user message asynchronously with per-request options.
     *
     * @param message user prompt
     * @param options per-request options such as {@code system} and {@code temperature}
     * @return future resolving to assistant reply
     */
    public CompletableFuture<String> chatAsync(String message, Map<String, String> options) {
        return CompletableFuture.supplyAsync(() -> processUserMessage(Message.ofUser(message), options), executor);
    }

    /**
     * Send a fully constructed message asynchronously.
     *
     * @param message message to append and process
     * @return future resolving to assistant reply
     */
    public CompletableFuture<String> chatAsync(Message message) {
        return CompletableFuture.supplyAsync(() -> processUserMessage(message, Map.of()), executor);
    }

    // ========== Streaming Chat ==========
    /**
     * Stream a chat reply token/chunk by token/chunk.
     *
     * @param message user prompt
     * @param onToken callback invoked for each text chunk
     */
    public void streamChat(String message, Consumer<String> onToken) {
        streamChat(message, onToken, e -> logger.error("Stream error: {}", e));
    }

    /**
     * Stream a chat reply while also receiving live lifecycle status events.
     *
     * <p>Use this overload for terminal, IDE, and agent-style applications that
     * need to show model/tool progress while the request is running.</p>
     *
     * @param message user prompt
     * @param onToken callback invoked for each text chunk or final tool-assisted reply
     * @param onStatus callback invoked for chat, streaming, and tool lifecycle events
     */
    public void streamChatWithStatus(String message, Consumer<String> onToken, Consumer<LLMStatus> onStatus) {
        Consumer<LLMStatus> previous = this.statusListener;
        this.statusListener = combineStatusListeners(previous, onStatus);
        try {
            streamChat(message, onToken);
        } finally {
            this.statusListener = previous;
        }
    }

    /**
     * Stream a chat reply with explicit error handling and per-call status events.
     *
     * @param message user prompt
     * @param onToken callback invoked for each text chunk or final tool-assisted reply
     * @param onError callback invoked with an error message if streaming fails
     * @param onStatus callback invoked for chat, streaming, and tool lifecycle events
     */
    public void streamChat(String message, Consumer<String> onToken, Consumer<String> onError, Consumer<LLMStatus> onStatus) {
        Consumer<LLMStatus> previous = this.statusListener;
        this.statusListener = combineStatusListeners(previous, onStatus);
        try {
            streamChat(message, onToken, onError);
        } finally {
            this.statusListener = previous;
        }
    }

    /**
     * Stream a chat reply with explicit error handling.
     *
     * @param message user prompt
     * @param onToken callback invoked for each text chunk
     * @param onError callback invoked with an error message if streaming fails
     */
    public void streamChat(String message, Consumer<String> onToken, Consumer<String> onError) {
        try {
            if (!tools.isEmpty()) {
                String reply = processUserMessage(Message.ofUser(message), Map.of());
                if (!reply.isEmpty()) onToken.accept(reply);
                return;
            }

            var response = new StringBuilder();
            emitStatus(LLMStatus.of(LLMStatus.Type.STREAM_STARTED, "Streaming chat started"));
            ensureContextCapacity(List.of(Message.ofUser(message)), Map.of());
            history.addUser(message);
            LLMRequest request = buildRequestFromHistory(Map.of());
            emitStatus(LLMStatus.of(LLMStatus.Type.REQUEST_SENT, "Streaming request sent"));
            adapter.streamChat(request, token -> {
                response.append(token);
                emitStatus(LLMStatus.result(LLMStatus.Type.STREAM_CHUNK, "Streaming chunk received", token));
                onToken.accept(token);
            });
            history.addAssistant(response.toString());
            syncTokenUsage(null);
            compactHistoryIfNeeded(Map.of());
            emitStatus(LLMStatus.result(LLMStatus.Type.STREAM_COMPLETED, "Streaming completed", response.toString()));
            emitStatus(LLMStatus.of(LLMStatus.Type.CHAT_COMPLETED, "Chat completed"));
        } catch (Exception e) {
            emitStatus(LLMStatus.error("Stream failed", e));
            onError.accept(e.getMessage());
            logger.error("Stream failed: {}", e.getMessage());
        }
    }

    // ========== History Management ==========
    /** @return current conversation history object */
    public ConversationHistory getHistory() { return history; }
    /** @return this client after clearing conversation history and token counters */
    public LLMClient clearHistory() {
        history.clear();
        tokenTracker.reset();
        compactedContextSummary = null;
        return this;
    }
    /** @param n number of latest messages to remove @return this client */
    public LLMClient clearLastN(int n) { history.clearLastN(n); return this; }

    /** @param conversationId Redis conversation key/id @return this client */
    public LLMClient withRedisHistory(String conversationId) {
        return withRedisHistory(conversationId, "localhost", 6379);
    }

    /**
     * Use Redis-backed history when Redis is available, otherwise fallback to in-memory history.
     *
     * @param conversationId Redis conversation key/id
     * @param host Redis host
     * @param port Redis port
     * @return this client
     */
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
        this.compactedContextSummary = extractCompactedSummary(this.history.getMessages());
        syncTokenUsage(null);
        return this;
    }

    /** @return this client after switching back to fresh in-memory history */
    public LLMClient withMemoryHistory() {
        this.history = new ConversationHistory();
        this.compactedContextSummary = null;
        this.tokenTracker.reset();
        return this;
    }

    // ========== Token Tracking ==========
    /** @return current context-window usage summary */
    public TokenTracker.ContextInfo getContextInfo() { return tokenTracker.getContextInfo(); }

    /**
     * Estimate context-window usage after adding a hypothetical next user message.
     *
     * @param nextUserMessage message to project
     * @return projected context usage summary
     */
    public TokenTracker.ContextInfo getProjectedContextInfo(String nextUserMessage) {
        var projected = new ArrayList<>(history.getMessages());
        projected.add(Message.ofUser(nextUserMessage));
        return estimateContextInfo(projected);
    }

    /** @return mutable token tracker used by this client */
    public TokenTracker getTokenTracker() { return tokenTracker; }
    /** @return last generated compacted context summary, or null */
    public String getCompactedContextSummary() { return compactedContextSummary; }
    /** Print current context-window usage to standard output. */
    public void printContextInfo() {
        System.out.println(TokenTracker.formatContextInfo(tokenTracker.getContextInfo()));
    }

    // ========== Configuration Methods ==========
    /** @param tools replacement tool schema list @return this client */
    public LLMClient withTools(List<Tool> tools) {
        this.tools.clear(); this.tools.addAll(tools); return this;
    }
    /** @param config retry configuration for future tool registrations @return this client */
    public LLMClient withRetry(Retry.RetryConfig config) {
        this.retryConfig = config; return this;
    }
    /** @param maxAttempts maximum retry attempts for future tool registrations @return this client */
    public LLMClient withRetry(int maxAttempts) {
        return withRetry(new Retry.RetryConfig(maxAttempts, 500, 2.0, 10_000));
    }
    /** @return this client with automatic history compaction enabled */
    public LLMClient withAutoCompaction() { this.autoCompactEnabled = true; return this; }
    /** @param triggerPercent usage percent that triggers compaction @param targetPercent post-compaction target percent @return this client */
    public LLMClient withAutoCompaction(double triggerPercent, double targetPercent) {
        this.autoCompactEnabled = true;
        this.autoCompactTriggerPercent = triggerPercent;
        this.autoCompactTargetPercent = targetPercent;
        return this;
    }
    /** @param triggerPercent usage percent that triggers compaction @param targetPercent post-compaction target percent @param keepLastMessages recent messages to keep verbatim @return this client */
    public LLMClient withAutoCompaction(double triggerPercent, double targetPercent, int keepLastMessages) {
        this.autoCompactEnabled = true;
        this.autoCompactTriggerPercent = triggerPercent;
        this.autoCompactTargetPercent = targetPercent;
        this.compactKeepLastMessages = keepLastMessages;
        return this;
    }
    /** @return this client with automatic compaction disabled */
    public LLMClient withoutAutoCompaction() { this.autoCompactEnabled = false; return this; }
    /** @param contextWindowTokens manual context window size @return this client */
    public LLMClient withContextWindow(long contextWindowTokens) {
        tokenTracker.setModel(config.model(), contextWindowTokens);
        manualContextWindowConfigured = true;
        return this;
    }
    /** @return this client with verbose debug logging enabled */
    public LLMClient withVerboseLogging() {
        logger.setVerbose(true);
        logger.setLevel(SimpleLogger.Level.DEBUG);
        return this;
    }
    /** @param level log level to use while verbose logging is enabled @return this client */
    public LLMClient withVerboseLogging(SimpleLogger.Level level) {
        logger.setVerbose(true);
        logger.setLevel(level);
        return this;
    }
    /** @return this client with verbose logging disabled */
    public LLMClient withoutVerboseLogging() { logger.setVerbose(false); return this; }
    /** @param level logger level @return this client */
    public LLMClient setLogLevel(SimpleLogger.Level level) { logger.setLevel(level); return this; }
    /** @return logger used by this client */
    public SimpleLogger getLogger() { return logger; }

    /**
     * Register a live status listener for all future requests made by this client.
     *
     * <p>The listener receives typed {@link LLMStatus} events for chat start,
     * request send, response receipt, streaming chunks, tool-call requests, tool
     * execution start/completion/failure, tool-response validation, continuation,
     * completion, and errors.</p>
     *
     * @param listener callback invoked for lifecycle events; {@code null} disables status callbacks
     * @return this client for fluent chaining
     */
    public LLMClient onStatus(Consumer<LLMStatus> listener) {
        this.statusListener = listener != null ? listener : status -> {};
        return this;
    }

    /** @return this client after removing the current live status listener */
    public LLMClient clearStatusListener() {
        this.statusListener = status -> {};
        return this;
    }

    /** Shut down the client's executor. Use after finishing with long-lived clients. */
    @Override
    public void close() {
        executor.shutdown();
    }

    // ========== Core Processing ==========
    private String processUserMessage(Message message, Map<String, String> options) {
        emitStatus(LLMStatus.of(LLMStatus.Type.CHAT_STARTED, "Chat started"));
        ensureContextCapacity(List.of(message), options);
        history.add(message);
        syncTokenUsage(null);
        logger.debugVerbose(() -> "Accepted user message | role=" + message.role()
            + " | chars=" + safeLength(message.content())
            + " | historyMessages=" + history.size());
        return processCurrentConversation(options);
    }

    private String processCurrentConversation(Map<String, String> options) {
        logger.debug("Processing conversation with {} messages", history.size());
        LLMRequest request = buildRequestFromHistory(options);
        logger.debugVerbose(() -> "Request details | model=" + request.model()
            + " | stream=" + request.stream()
            + " | messages=" + request.messages().size()
            + " | tools=" + request.tools().size()
            + " | context=" + tokenTracker.getContextInfo().summary());
        emitStatus(LLMStatus.of(LLMStatus.Type.REQUEST_SENT, "Request sent to model"));
        LLMResponse response = adapter.chat(request);
        emitStatus(LLMStatus.of(LLMStatus.Type.RESPONSE_RECEIVED, "Model response received"));
        logger.debugVerbose(() -> "Response details | model=" + response.model()
            + " | finishReason=" + response.finishReason()
            + " | contentChars=" + safeLength(response.getContentOrEmpty()));

        if (response.hasToolCalls()) {
            logger.debug("Handling {} tool calls", response.getToolCalls().size());
            response.getToolCalls().forEach(call -> emitStatus(LLMStatus.tool(
                LLMStatus.Type.TOOL_CALL_REQUESTED,
                call.function() != null ? call.function().name() : "unknown",
                call.function() != null ? call.function().arguments() : Map.of())));
            history.add(response.message());
            syncTokenUsage(response);
            return handleToolCallsAsync(response.getToolCalls()).join();
        }
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        syncTokenUsage(response);
        compactHistoryIfNeeded(options);
        logger.debug("Response: {} chars", reply.length());
        emitStatus(LLMStatus.result(LLMStatus.Type.CHAT_COMPLETED, "Chat completed", reply));
        return reply;
    }

    // ========== Tool Execution with Retry ==========
    private CompletableFuture<String> handleToolCallsAsync(List<ToolCall> calls) {
        logger.debugVerbose(() -> "Tool call batch | names=" + calls.stream()
            .map(call -> call.function() != null ? call.function().name() : "unknown")
            .collect(Collectors.joining(", ")));
        List<CompletableFuture<String>> futures = calls.stream()
            .map(this::executeToolWithRetryAsync)
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> IntStream.range(0, futures.size())
                .mapToObj(i -> getFutureResult(futures.get(i)))
                .toList())
            .thenCompose(results -> {
                IntStream.range(0, calls.size()).forEach(i -> {
                    ToolCall call = calls.get(i);
                    Message toolMsg = Message.ofTool(results.get(i));
                    toolMsg = toolMsg.withName(call.function() != null ? call.function().name() : null);
                    history.add(toolMsg);
                    emitStatus(LLMStatus.toolResult(
                        LLMStatus.Type.TOOL_RESPONSE_APPENDED,
                        call.function() != null ? call.function().name() : "unknown",
                        call.function() != null ? call.function().arguments() : Map.of(),
                        results.get(i)));
                });
                syncTokenUsage(null);
                return continueConversation();
            });
    }

    private CompletableFuture<String> executeToolWithRetryAsync(ToolCall call) {
        return CompletableFuture.supplyAsync(() -> {
            String toolName = call.function().name();
            Map<String, Object> args = call.function().arguments();
            ToolRegistry.ToolInfo info = toolRegistry.get(toolName);
            emitStatus(LLMStatus.tool(LLMStatus.Type.TOOL_EXECUTION_STARTED, toolName, args));

            if (info == null) {
                logger.warn("Tool not found: {}", toolName);
                String result = buildToolFailureJson(toolName, args, "Tool not found: " + toolName);
                emitStatus(LLMStatus.toolResult(LLMStatus.Type.TOOL_EXECUTION_FAILED, toolName, args, result));
                emitStatus(LLMStatus.toolResult(LLMStatus.Type.TOOL_RESPONSE_VALIDATED, toolName, args, result));
                return result;
            }

            try {
                Object rawResult = info.invoke(args);
                String structuredResult = buildToolResultJson(toolName, args, rawResult);
                emitStatus(LLMStatus.toolResult(LLMStatus.Type.TOOL_EXECUTION_COMPLETED, toolName, args, structuredResult));
                emitStatus(LLMStatus.toolResult(LLMStatus.Type.TOOL_RESPONSE_VALIDATED, toolName, args, structuredResult));
                return structuredResult;
            } catch (Exception e) {
                String result = buildToolFailureJson(toolName, args, e.getMessage());
                emitStatus(LLMStatus.toolResult(LLMStatus.Type.TOOL_EXECUTION_FAILED, toolName, args, result));
                emitStatus(LLMStatus.toolResult(LLMStatus.Type.TOOL_RESPONSE_VALIDATED, toolName, args, result));
                emitStatus(LLMStatus.error("Tool execution failed: " + toolName, e));
                return result;
            }
        }, executor);
    }

    private CompletableFuture<String> continueConversation() {
        return CompletableFuture.supplyAsync(() -> {
            compactHistoryIfNeeded(Map.of());
            LLMRequest request = buildRequestFromHistory(Map.of());
            emitStatus(LLMStatus.of(LLMStatus.Type.CONTINUATION_STARTED, "Continuing conversation after tool results"));
            emitStatus(LLMStatus.of(LLMStatus.Type.REQUEST_SENT, "Continuation request sent to model"));
            LLMResponse response = adapter.chat(request);
            emitStatus(LLMStatus.of(LLMStatus.Type.RESPONSE_RECEIVED, "Continuation response received"));
            String reply = response.getContentOrEmpty();
            history.addAssistant(reply);
            syncTokenUsage(response);
            compactHistoryIfNeeded(Map.of());
            emitStatus(LLMStatus.result(LLMStatus.Type.CHAT_COMPLETED, "Chat completed", reply));
            return reply;
        }, executor);
    }

    // ========== Request Building ==========
    private LLMRequest buildRequestFromHistory(Map<String, String> options) {
        var requestMessages = prependSystemMessage(history.getMessages(), options);
        return LLMRequest.builder()
            .model(config.model())
            .stream(config.stream())
            .messages(requestMessages)
            .temperature(parseTemperature(options))
            .tools(tools.isEmpty() ? null : tools)
            .build();
    }

    private TokenTracker.ContextInfo estimateContextInfo(List<Message> messages) {
        long totalLimit = tokenTracker.getModelLimit() > 0
            ? tokenTracker.getModelLimit()
            : TokenTracker.detectLimitForModel(config.model());
        int used = tokenTracker.estimateTokens(messages);
        int remaining = (int) Math.max(0, totalLimit - used);
        double usagePercent = totalLimit > 0 ? (used * 100.0 / totalLimit) : 0;
        return new TokenTracker.ContextInfo(
            config.model(), totalLimit, used, remaining, used, 0, usagePercent, messages.size());
    }

    private void syncTokenUsage(LLMResponse response) {
        var currentMessages = history.getMessages();
        if (response != null && response.model() != null) {
            long contextLimit = manualContextWindowConfigured
                ? tokenTracker.getModelLimit()
                : TokenTracker.detectLimitForModel(response.model());
            tokenTracker.setModel(response.model(), contextLimit);
        } else if (config.model() != null && tokenTracker.getModel() == null) {
            tokenTracker.setModel(config.model(), TokenTracker.detectLimitForModel(config.model()));
        }

        if (response != null && response.usage() != null) {
            tokenTracker.updateFromUsage(response, currentMessages.size());
        } else {
            tokenTracker.syncWithConversation(currentMessages);
        }
        logger.debugVerbose(() -> "Token usage synced | " + tokenTracker.getContextInfo().summary());
    }

    private void ensureContextCapacity(List<Message> pendingMessages, Map<String, String> options) {
        if (!autoCompactEnabled || compactingHistory) return;

        var projected = new ArrayList<>(prependSystemMessage(history.getMessages(), options));
        projected.addAll(pendingMessages);

        TokenTracker.ContextInfo projectedInfo = estimateContextInfo(projected);
        if (projectedInfo.usagePercent() < autoCompactTriggerPercent) return;

        logger.info("Context usage at {}%. Compacting history before request.", String.format("%.1f", projectedInfo.usagePercent()));
        compactHistoryUntilWithinTarget(options, pendingMessages);
    }

    private void compactHistoryIfNeeded(Map<String, String> options) {
        if (!autoCompactEnabled || compactingHistory) return;
        TokenTracker.ContextInfo info = estimateContextInfo(history.getMessages());
        if (info.usagePercent() >= autoCompactTriggerPercent) {
            logger.info("Context usage remains high after response ({}%). Compacting.", String.format("%.1f", info.usagePercent()));
            compactHistoryUntilWithinTarget(options, List.of());
        }
    }

    private void compactHistoryUntilWithinTarget(Map<String, String> options, List<Message> pendingMessages) {
        for (int attempt = 0; attempt < MAX_COMPACTION_ATTEMPTS; attempt++) {
            var projected = new ArrayList<>(prependSystemMessage(history.getMessages(), options));
            projected.addAll(pendingMessages);

            TokenTracker.ContextInfo info = estimateContextInfo(projected);
            if (info.usagePercent() <= autoCompactTargetPercent) return;
            String summary = compactHistoryInternal();
            if (summary == null || summary.isBlank()) return;
        }
    }

    public String compactHistoryNow() { return compactHistoryInternal(); }

    private String compactHistoryInternal() {
        if (compactingHistory || history.size() <= 2) return compactedContextSummary;

        compactingHistory = true;
        try {
            var existingMessages = history.getMessages();
            String transcript = formatTranscript(existingMessages);
            if (transcript.isBlank()) return compactedContextSummary;

            var summaryMessages = new ArrayList<Message>();
            summaryMessages.add(Message.ofSystem(COMPACTION_SYSTEM_PROMPT));
            if (compactedContextSummary != null && !compactedContextSummary.isBlank()) {
                summaryMessages.add(Message.ofUser("Existing rolling summary:\n" + compactedContextSummary));
            }
            summaryMessages.add(Message.ofUser("Conversation transcript to compress:\n" + transcript));

            LLMRequest summaryRequest = LLMRequest.builder()
                .model(config.model())
                .messages(summaryMessages)
                .stream(false)
                .build();

            LLMResponse response = adapter.chat(summaryRequest);
            String summary = response != null ? response.getContentOrEmpty().trim() : "";
            if (summary.isBlank()) return compactedContextSummary;

            compactedContextSummary = summary;
            logger.infoVerbose(() -> "Compaction summary generated | chars=" + summary.length());

            // Preserve system messages that aren't compacted summaries
            var newHistory = existingMessages.stream()
                .filter(m -> m.role() != Message.Role.system || m.content() == null || m.content().startsWith(COMPACTED_CONTEXT_PREFIX))
                .collect(Collectors.toCollection(ArrayList::new));

            newHistory.add(Message.ofSystem(COMPACTED_CONTEXT_PREFIX + summary));

            var nonSystemRecent = existingMessages.stream()
                .filter(m -> m.role() != Message.Role.system)
                .toList();
            var recentMessages = nonSystemRecent.subList(
                Math.max(0, nonSystemRecent.size() - compactKeepLastMessages),
                nonSystemRecent.size());
            newHistory.addAll(recentMessages);

            history.replaceAll(newHistory);
            syncTokenUsage(null);
            logger.info("Conversation compacted to {} messages ({} tokens estimated).",
                history.size(), tokenTracker.getContextInfo().usedTokens());
            return summary;
        } finally {
            compactingHistory = false;
        }
    }

    private String formatTranscript(List<Message> messages) {
        return messages.stream()
            .map(this::formatTranscriptMessage)
            .filter(line -> !line.isBlank())
            .collect(Collectors.joining("\n\n"))
            .trim();
    }

    private String formatTranscriptMessage(Message message) {
        boolean hasToolCalls = !message.toolCalls().isEmpty();
        if ((message.content() == null || message.content().isBlank()) && !hasToolCalls) return "";

        var line = new StringBuilder(message.role().name().toUpperCase());
        if (message.name() != null && !message.name().isBlank()) {
            line.append(" (").append(message.name()).append(")");
        }
        line.append(": ");

        if (message.content() != null && !message.content().isBlank()) line.append(message.content());
        if (hasToolCalls) {
            var toolLines = message.toolCalls().stream()
                .map(call -> "Tool call -> " + call.function().name() + " " + call.function().arguments())
                .collect(Collectors.joining("\n"));
            if (message.content() != null && !message.content().isBlank()) line.append("\n");
            line.append(toolLines);
        }
        return line.toString();
    }

    private List<Message> prependSystemMessage(List<Message> messages, Map<String, String> options) {
        String system = options.get("system");
        if (system == null || system.isBlank()) return messages;

        var requestMessages = new ArrayList<Message>();
        requestMessages.add(Message.ofSystem(system));
        requestMessages.addAll(messages);
        return requestMessages;
    }

    private Double parseTemperature(Map<String, String> options) {
        String temperature = options.get("temperature");
        return temperature != null ? Double.parseDouble(temperature) : null;
    }

    private String getFutureResult(CompletableFuture<String> future) {
        try { return future.get(); }
        catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    private String extractCompactedSummary(List<Message> messages) {
        return messages.stream()
            .filter(m -> m.role() == Message.Role.system)
            .map(Message::content)
            .filter(Objects::nonNull)
            .filter(c -> c.startsWith(COMPACTED_CONTEXT_PREFIX))
            .map(c -> c.substring(COMPACTED_CONTEXT_PREFIX.length()))
            .reduce((first, second) -> second)
            .orElse(null);
    }

    private int safeLength(String value) { return value != null ? value.length() : 0; }

    private void emitStatus(LLMStatus status) {
        try {
            statusListener.accept(status);
        } catch (Exception e) {
            logger.warn("Status listener failed: {}", e.getMessage());
        }
    }

    private Consumer<LLMStatus> combineStatusListeners(Consumer<LLMStatus> first, Consumer<LLMStatus> second) {
        Consumer<LLMStatus> safeFirst = first != null ? first : status -> {};
        Consumer<LLMStatus> safeSecond = second != null ? second : status -> {};
        return status -> {
            safeFirst.accept(status);
            safeSecond.accept(status);
        };
    }

    private String buildToolResultJson(String toolName, Map<String, Object> args, Object rawResult) {
        ToolValidation validation = validateToolResult(rawResult);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("ok", validation.ok());
        payload.put("status", validation.ok() ? "success" : "failed");
        payload.put("arguments", args);
        payload.put("result", rawResult);
        payload.put("message", validation.message());
        if (!validation.ok()) {
            payload.put("user_message", "The " + toolName + " tool could not return a usable result. Explain this clearly to the user instead of giving a vague answer.");
        }
        return new com.google.gson.Gson().toJson(payload);
    }

    private String buildToolFailureJson(String toolName, Map<String, Object> args, String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("ok", false);
        payload.put("status", "failed");
        payload.put("arguments", args);
        payload.put("error", error != null && !error.isBlank() ? error : "Tool execution failed");
        payload.put("message", "The " + toolName + " tool failed before it could return a usable result.");
        payload.put("user_message", "Tell the user the " + toolName + " tool failed and include the error in plain language.");
        return new com.google.gson.Gson().toJson(payload);
    }

    private ToolValidation validateToolResult(Object rawResult) {
        if (rawResult == null) {
            return new ToolValidation(false, "Tool returned null instead of usable data.");
        }
        if (rawResult instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return new ToolValidation(false, "Tool returned an empty response.");
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean unavailable = List.of(
                "sry", "sorry", "not able", "unable", "can't", "cannot",
                "failed", "error", "not available", "no data", "try again later"
            ).stream().anyMatch(lower::contains);
            if (unavailable) {
                return new ToolValidation(false, "Tool returned an unavailable or failure-like response instead of useful data.");
            }
        }
        return new ToolValidation(true, "Tool completed and returned usable data.");
    }

    private record ToolValidation(boolean ok, String message) {}

    // ========== Tool Conversion ==========
    private Tool toolFromInfo(ToolRegistry.ToolInfo info) {
        var fn = new Tool.Function(
            info.name(), info.description(),
            info.params().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new Tool.Function.Param(ToolRegistry.ParamInfo.jsonType(e.getValue().type()), e.getValue().description())))
        );
        return new Tool("function", fn);
    }

    // ========== Static Factory Methods ==========
    /**
     * Create a client from a full config.
     *
     * @param config provider, model, key, timeout, and sampling config
     * @return configured client
     */
    public static LLMClient create(ClientConfig config) { return builder().config(config).build(); }

    /** @param model Ollama model name @return configured Ollama client */
    public static LLMClient ollama(String model) { return create(ClientConfig.of(Provider.OLLAMA).model(model)); }
    /** @param baseUrl Ollama base URL @param model Ollama model name @return configured Ollama client */
    public static LLMClient ollama(String baseUrl, String model) { return create(ClientConfig.of(Provider.OLLAMA).baseUrl(baseUrl).model(model)); }
    /** @param model OpenAI model name @param apiKey OpenAI API key @return configured OpenAI client */
    public static LLMClient openAI(String model, String apiKey) { return create(ClientConfig.of(Provider.OPENAI).model(model).apiKey(apiKey)); }
    /** @param model DeepSeek model name @param apiKey DeepSeek API key @return configured DeepSeek client */
    public static LLMClient deepSeek(String model, String apiKey) { return create(ClientConfig.of(Provider.DEEPSEEK).model(model).apiKey(apiKey)); }
    /** @param model Claude model name @param apiKey Anthropic API key @return configured Claude client */
    public static LLMClient claude(String model, String apiKey) { return create(ClientConfig.of(Provider.ANTHROPIC).model(model).apiKey(apiKey)); }
    /** @param model NVIDIA model name @param apiKey NVIDIA API key @return configured NVIDIA client */
    public static LLMClient nvidia(String model, String apiKey) { return create(ClientConfig.of(Provider.NVIDIA).model(model).apiKey(apiKey)); }
    /** @param model OpenRouter model name @param apiKey OpenRouter API key @return configured OpenRouter client */
    public static LLMClient openRouter(String model, String apiKey) { return create(ClientConfig.of(Provider.OPENROUTER).model(model).apiKey(apiKey)); }
    /** @param model LM Studio model name @return configured LM Studio client */
    public static LLMClient lmStudio(String model) { return create(ClientConfig.of(Provider.LM_STUDIO).model(model)); }
    /** @param model vLLM model name @return configured vLLM client */
    public static LLMClient vllm(String model) { return create(ClientConfig.of(Provider.VLLM).model(model)); }
    /** @param model Jan model name @return configured Jan client */
    public static LLMClient jan(String model) { return create(ClientConfig.of(Provider.JAN).model(model)); }
    /** @param model Groq model name @param apiKey Groq API key @return configured Groq client */
    public static LLMClient groq(String model, String apiKey) { return create(ClientConfig.of(Provider.GROQ).model(model).apiKey(apiKey)); }
    /** @param model Mistral model name @param apiKey Mistral API key @return configured Mistral client */
    public static LLMClient mistral(String model, String apiKey) { return create(ClientConfig.of(Provider.MISTRAL).model(model).apiKey(apiKey)); }

    private static ProviderAdapter createAdapter(ClientConfig config) {
        return switch (config.provider()) {
            case OLLAMA -> new OllamaAdapter(config.baseUrl(), config.model());
            case ANTHROPIC -> new ClaudeAdapter(config.baseUrl(), config.model(), config.apiKey());
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
        @Override public void replaceAll(List<Message> messages) {
            store.clear();
            if (messages != null) messages.forEach(store::add);
        }
        @Override public int size() { return store.size(); }
    }
}
