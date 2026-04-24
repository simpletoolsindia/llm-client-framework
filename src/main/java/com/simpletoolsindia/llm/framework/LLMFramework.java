package com.simpletoolsindia.llm.framework;

import com.simpletoolsindia.llm.framework.client.*;
import com.simpletoolsindia.llm.framework.config.*;
import com.simpletoolsindia.llm.framework.history.ConversationHistory;
import java.util.*;

public class LLMFramework {
    private final LLMClient client;

    public LLMFramework(LLMClient client) {
        this.client = client;
    }

    public static LLMFramework create(ClientConfig config) {
        return new LLMFramework(LLMClient.create(config));
    }

    public static LLMFramework create(Provider provider) {
        return new LLMFramework(LLMClient.create(ClientConfig.of(provider)));
    }

    public static LLMFramework run(String provider, String model, String apiKey, Runnable task) {
        LLMClient client = LLMClientFactory.create(
            Provider.valueOf(provider.toUpperCase()),
            Provider.valueOf(provider.toUpperCase()).getDefaultBaseUrl(),
            model,
            apiKey
        );
        try {
            task.run();
        } finally {
            // cleanup if needed
        }
        return new LLMFramework(client);
    }

    public static LLMFramework of(Provider provider, String model) {
        return create(ClientConfig.of(provider).model(model));
    }

    public static LLMFramework of(Provider provider, String model, String apiKey) {
        return create(ClientConfig.of(provider).model(model).apiKey(apiKey));
    }

    public LLMClient client() { return client; }

    public String chat(String message) { return client.chat(message); }
    public String chat(String message, Map<String, String> options) { return client.chat(message, options); }
    public void streamChat(String message, java.util.function.Consumer<String> onChunk) {
        client.streamChat(message, onChunk);
    }
    public ConversationHistory history() { return client.getHistory(); }
    public void clearHistory() { client.clearHistory(); }
}