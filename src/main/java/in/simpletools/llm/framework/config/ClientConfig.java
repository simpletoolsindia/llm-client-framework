package in.simpletools.llm.framework.config;

/**
 * Immutable client configuration with fluent with-copy methods.
 */
public record ClientConfig(
    Provider provider,
    String baseUrl,
    String model,
    String apiKey,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Double frequencyPenalty,
    Double presencePenalty,
    String[] stopSequences,
    Double timeout,
    int connectTimeoutMs,
    int readTimeoutMs,
    boolean stream
) {
    public ClientConfig {
        if (stopSequences == null) stopSequences = new String[0];
    }

    public static ClientConfig of(Provider provider) {
        return new ClientConfig(provider, provider.getDefaultBaseUrl(), null, null,
            null, null, null, null, null, new String[0], null,
            30_000, 60_000, true);
    }

    public ClientConfig provider(Provider p) { return new ClientConfig(p, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig baseUrl(String url) { return new ClientConfig(provider, url, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig model(String m) { return new ClientConfig(provider, baseUrl, m, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig apiKey(String key) { return new ClientConfig(provider, baseUrl, model, key, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig temperature(Double t) { return new ClientConfig(provider, baseUrl, model, apiKey, t, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig maxTokens(Integer m) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, m, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig topP(Double p) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, p, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig frequencyPenalty(Double p) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, p, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig presencePenalty(Double p) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, p, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig stop(String... s) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, s, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig timeout(Double seconds) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, seconds, connectTimeoutMs, readTimeoutMs, stream); }
    public ClientConfig connectTimeoutMs(int ms) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, ms, readTimeoutMs, stream); }
    public ClientConfig readTimeoutMs(int ms) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, ms, stream); }

    public ClientConfig timeoutSeconds(int seconds) {
        return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, seconds * 1000, seconds * 1000, stream);
    }

    public ClientConfig stream(boolean s) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, s); }

    public ClientConfig ollama(String baseUrl, String model) { return provider(Provider.OLLAMA).baseUrl(baseUrl).model(model); }
    public ClientConfig openAI(String model, String apiKey) { return provider(Provider.OPENAI).model(model).apiKey(apiKey); }
    public ClientConfig claude(String model, String apiKey) { return provider(Provider.ANTHROPIC).model(model).apiKey(apiKey); }
    public ClientConfig deepSeek(String model, String apiKey) { return provider(Provider.DEEPSEEK).model(model).apiKey(apiKey); }
    public ClientConfig nvidia(String model, String apiKey) { return provider(Provider.NVIDIA).model(model).apiKey(apiKey); }
    public ClientConfig openRouter(String model, String apiKey) { return provider(Provider.OPENROUTER).model(model).apiKey(apiKey); }
    public ClientConfig lmStudio(String model) { return provider(Provider.LM_STUDIO).model(model); }
    public ClientConfig vllm(String model) { return provider(Provider.VLLM).model(model); }
}
