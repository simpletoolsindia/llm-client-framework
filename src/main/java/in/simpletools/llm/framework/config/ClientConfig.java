package in.simpletools.llm.framework.config;

/**
 * Immutable client configuration with fluent with-copy methods.
 *
 * <p>Each setter returns a new {@code ClientConfig}, so it is safe to share and
 * reuse base configs.</p>
 *
 * @param provider target LLM provider
 * @param baseUrl provider API base URL
 * @param model model name sent to the provider
 * @param apiKey provider API key, when required
 * @param temperature sampling temperature, when supported
 * @param maxTokens maximum output tokens, when supported
 * @param topP nucleus sampling value, when supported
 * @param frequencyPenalty frequency penalty, when supported
 * @param presencePenalty presence penalty, when supported
 * @param stopSequences stop sequences that end generation
 * @param timeout provider timeout hint in seconds, when supported
 * @param connectTimeoutMs HTTP connection timeout in milliseconds
 * @param readTimeoutMs HTTP read timeout in milliseconds
 * @param stream whether requests should ask for streaming responses when supported
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

    /**
     * Create a config for a provider using its default base URL.
     *
     * <pre>{@code
     * ClientConfig config = ClientConfig.of(Provider.OLLAMA)
     *     .model("gemma4:latest")
     *     .timeoutSeconds(60);
     * }</pre>
     *
     * @param provider target LLM provider
     * @return config with provider defaults and no model/API key yet
     */
    public static ClientConfig of(Provider provider) {
        return new ClientConfig(provider, provider.getDefaultBaseUrl(), null, null,
            null, null, null, null, null, new String[0], null,
            30_000, 60_000, true);
    }

    /**
     * Return a copy with a different provider.
     *
     * @param p target LLM provider; update the base URL separately if needed
     * @return updated config
     */
    public ClientConfig provider(Provider p) { return new ClientConfig(p, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a different provider API base URL.
     *
     * @param url provider API base URL, for example {@code http://localhost:11434} for Ollama
     * @return updated config
     */
    public ClientConfig baseUrl(String url) { return new ClientConfig(provider, url, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a different model.
     *
     * @param m model name, for example {@code gemma4:latest} or {@code gpt-4o-mini}
     * @return updated config
     */
    public ClientConfig model(String m) { return new ClientConfig(provider, baseUrl, m, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a provider API key.
     *
     * @param key provider API key; not required for local providers like Ollama
     * @return updated config
     */
    public ClientConfig apiKey(String key) { return new ClientConfig(provider, baseUrl, model, key, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a sampling temperature.
     *
     * @param t sampling temperature; lower is more deterministic, higher is more creative
     * @return updated config
     */
    public ClientConfig temperature(Double t) { return new ClientConfig(provider, baseUrl, model, apiKey, t, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a maximum output token limit.
     *
     * @param m maximum output tokens requested from the provider
     * @return updated config
     */
    public ClientConfig maxTokens(Integer m) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, m, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a nucleus sampling value.
     *
     * @param p nucleus sampling value, usually between {@code 0.0} and {@code 1.0}
     * @return updated config
     */
    public ClientConfig topP(Double p) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, p, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a frequency penalty.
     *
     * @param p frequency penalty value when supported by the provider
     * @return updated config
     */
    public ClientConfig frequencyPenalty(Double p) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, p, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a presence penalty.
     *
     * @param p presence penalty value when supported by the provider
     * @return updated config
     */
    public ClientConfig presencePenalty(Double p) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, p, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with stop sequences.
     *
     * @param s stop sequences that end generation when emitted
     * @return updated config
     */
    public ClientConfig stop(String... s) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, s, timeout, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with a provider timeout hint.
     *
     * @param seconds provider request timeout hint in seconds when supported
     * @return updated config
     */
    public ClientConfig timeout(Double seconds) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, seconds, connectTimeoutMs, readTimeoutMs, stream); }
    /**
     * Return a copy with an HTTP connection timeout.
     *
     * @param ms HTTP connection timeout in milliseconds
     * @return updated config
     */
    public ClientConfig connectTimeoutMs(int ms) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, ms, readTimeoutMs, stream); }
    /**
     * Return a copy with an HTTP read timeout.
     *
     * @param ms HTTP read timeout in milliseconds
     * @return updated config
     */
    public ClientConfig readTimeoutMs(int ms) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, ms, stream); }

    /**
     * Set both connect and read timeouts to the same number of seconds.
     *
     * @param seconds timeout in seconds
     * @return updated config
     */
    public ClientConfig timeoutSeconds(int seconds) {
        return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, seconds * 1000, seconds * 1000, stream);
    }

    /**
     * Return a copy with streaming enabled or disabled.
     *
     * @param s whether provider requests should ask for streaming responses when supported
     * @return updated config
     */
    public ClientConfig stream(boolean s) { return new ClientConfig(provider, baseUrl, model, apiKey, temperature, maxTokens, topP, frequencyPenalty, presencePenalty, stopSequences, timeout, connectTimeoutMs, readTimeoutMs, s); }

    /**
     * Configure Ollama with an explicit base URL and model.
     *
     * @param baseUrl Ollama base URL, commonly {@code http://localhost:11434}
     * @param model Ollama model name
     * @return updated config
     */
    public ClientConfig ollama(String baseUrl, String model) { return provider(Provider.OLLAMA).baseUrl(baseUrl).model(model); }
    /**
     * Configure OpenAI with a model and API key.
     *
     * @param model OpenAI model name
     * @param apiKey OpenAI API key
     * @return updated config
     */
    public ClientConfig openAI(String model, String apiKey) { return provider(Provider.OPENAI).model(model).apiKey(apiKey); }
    /**
     * Configure Anthropic Claude with a model and API key.
     *
     * @param model Claude model name
     * @param apiKey Anthropic API key
     * @return updated config
     */
    public ClientConfig claude(String model, String apiKey) { return provider(Provider.ANTHROPIC).model(model).apiKey(apiKey); }
    /**
     * Configure DeepSeek with a model and API key.
     *
     * @param model DeepSeek model name
     * @param apiKey DeepSeek API key
     * @return updated config
     */
    public ClientConfig deepSeek(String model, String apiKey) { return provider(Provider.DEEPSEEK).model(model).apiKey(apiKey); }
    /**
     * Configure NVIDIA with a model and API key.
     *
     * @param model NVIDIA model name
     * @param apiKey NVIDIA API key
     * @return updated config
     */
    public ClientConfig nvidia(String model, String apiKey) { return provider(Provider.NVIDIA).model(model).apiKey(apiKey); }
    /**
     * Configure OpenRouter with a model and API key.
     *
     * @param model OpenRouter model name
     * @param apiKey OpenRouter API key
     * @return updated config
     */
    public ClientConfig openRouter(String model, String apiKey) { return provider(Provider.OPENROUTER).model(model).apiKey(apiKey); }
    /**
     * Configure LM Studio with a model and the provider default local base URL.
     *
     * @param model LM Studio model name
     * @return updated config
     */
    public ClientConfig lmStudio(String model) { return provider(Provider.LM_STUDIO).model(model); }
    /**
     * Configure vLLM with a model and the provider default local base URL.
     *
     * @param model vLLM model name
     * @return updated config
     */
    public ClientConfig vllm(String model) { return provider(Provider.VLLM).model(model); }
}
