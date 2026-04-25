package in.simpletools.llm.framework;

import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.history.ConversationHistory;
import java.util.*;

public class LLMFramework {
    private final LLMClient client;

    public LLMFramework(LLMClient client) { this.client = client; }

    public static LLMFramework create(in.simpletools.llm.framework.config.ClientConfig config) {
        return new LLMFramework(LLMClient.create(config));
    }

    public static LLMFramework create(in.simpletools.llm.framework.config.Provider provider) {
        return new LLMFramework(LLMClient.create(in.simpletools.llm.framework.config.ClientConfig.of(provider)));
    }

    public static LLMFramework of(in.simpletools.llm.framework.config.Provider provider, String model) {
        return create(in.simpletools.llm.framework.config.ClientConfig.of(provider).model(model));
    }

    public LLMClient client() { return client; }
    public String chat(String message) { return client.chat(message); }
    public String chat(String message, Map<String, String> options) { return client.chat(message, options); }
    public void streamChat(String message, java.util.function.Consumer<String> onChunk) { client.streamChat(message, onChunk); }
    public ConversationHistory history() { return client.getHistory(); }
    public void clearHistory() { client.clearHistory(); }
}
