package in.simpletools.llm.framework;

import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.history.ConversationHistory;
import java.util.*;

/**
 * Minimal facade around {@link LLMClient} for applications that want a compact API.
 *
 * <p>{@code LLMFramework} delegates to an underlying {@link LLMClient}. It is
 * useful for simple applications, examples, and scripting-style code. Use
 * {@link LLMClient} directly when you need full control over tools, retry,
 * logging, context windows, Redis history, or provider adapters.</p>
 *
 * <pre>{@code
 * LLMFramework framework = LLMFramework.of(Provider.OLLAMA, "gemma4:latest");
 * String reply = framework.chat("Write a short haiku about Java.");
 * }</pre>
 */
public class LLMFramework {
    private final LLMClient client;

    /**
     * Wrap an existing client.
     *
     * @param client configured client to delegate to
     */
    public LLMFramework(LLMClient client) { this.client = client; }

    /**
     * Create a facade from a full client config.
     *
     * @param config provider, model, key, and timeout configuration
     * @return framework facade backed by a new client
     */
    public static LLMFramework create(in.simpletools.llm.framework.config.ClientConfig config) {
        return new LLMFramework(LLMClient.create(config));
    }

    /**
     * Create a facade with provider defaults.
     *
     * @param provider provider to use
     * @return framework facade backed by a new client
     */
    public static LLMFramework create(in.simpletools.llm.framework.config.Provider provider) {
        return new LLMFramework(LLMClient.create(in.simpletools.llm.framework.config.ClientConfig.of(provider)));
    }

    /**
     * Create a facade from a provider and model name.
     *
     * @param provider provider to use
     * @param model model name to send to the provider
     * @return framework facade backed by a new client
     */
    public static LLMFramework of(in.simpletools.llm.framework.config.Provider provider, String model) {
        return create(in.simpletools.llm.framework.config.ClientConfig.of(provider).model(model));
    }

    /** @return underlying client for advanced configuration */
    public LLMClient client() { return client; }
    /** @param message user prompt @return assistant response text */
    public String chat(String message) { return client.chat(message); }
    /** @param message user prompt @param options per-request options @return assistant response text */
    public String chat(String message, Map<String, String> options) { return client.chat(message, options); }
    /** @param message user prompt @param onChunk callback invoked for each streamed text chunk */
    public void streamChat(String message, java.util.function.Consumer<String> onChunk) { client.streamChat(message, onChunk); }
    /** @return current conversation history */
    public ConversationHistory history() { return client.getHistory(); }
    /** Remove all messages from the current conversation history. */
    public void clearHistory() { client.clearHistory(); }
}
