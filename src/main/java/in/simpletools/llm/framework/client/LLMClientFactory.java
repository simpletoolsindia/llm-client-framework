package in.simpletools.llm.framework.client;

import in.simpletools.llm.framework.config.*;

/**
 * Convenience factory for common {@link LLMClient} configurations.
 *
 * <p>The static methods here mirror the static factory methods on
 * {@link LLMClient}. They are useful when codebases prefer a dedicated factory
 * class or when dependency injection setup wants one place for client creation.</p>
 *
 * <pre>{@code
 * LLMClient local = LLMClientFactory.ollama("gemma4:latest");
 * LLMClient cloud = LLMClientFactory.openAI("gpt-4o-mini", System.getenv("OPENAI_API_KEY"));
 * }</pre>
 */
public class LLMClientFactory {

    /**
     * Create a client with explicit provider details.
     *
     * @param provider provider enum
     * @param baseUrl API base URL
     * @param model model name
     * @param apiKey API key, or null for local providers that do not need one
     * @return configured client
     */
    public static LLMClient create(Provider provider, String baseUrl, String model, String apiKey) {
        return LLMClient.create(ClientConfig.of(provider).baseUrl(baseUrl).model(model).apiKey(apiKey));
    }

    /** @param config full client config @return configured client */
    public static LLMClient create(ClientConfig config) { return LLMClient.create(config); }
    /** @return Ollama client using provider defaults; set a model before chat when needed */
    public static LLMClient ollama() { return create(ClientConfig.of(Provider.OLLAMA)); }
    /** @param model Ollama model name @return configured Ollama client */
    public static LLMClient ollama(String model) { return create(ClientConfig.of(Provider.OLLAMA).model(model)); }
    /** @param baseUrl Ollama base URL @param model Ollama model name @return configured Ollama client */
    public static LLMClient ollama(String baseUrl, String model) { return create(ClientConfig.of(Provider.OLLAMA).baseUrl(baseUrl).model(model)); }
    /** @return LM Studio client using provider defaults */
    public static LLMClient lmStudio() { return create(ClientConfig.of(Provider.LM_STUDIO)); }
    /** @param model LM Studio model name @return configured LM Studio client */
    public static LLMClient lmStudio(String model) { return create(ClientConfig.of(Provider.LM_STUDIO).model(model)); }
    /** @return vLLM client using provider defaults */
    public static LLMClient vllm() { return create(ClientConfig.of(Provider.VLLM)); }
    /** @param model vLLM model name @return configured vLLM client */
    public static LLMClient vllm(String model) { return create(ClientConfig.of(Provider.VLLM).model(model)); }
    /** @return Jan client using provider defaults */
    public static LLMClient jan() { return create(ClientConfig.of(Provider.JAN)); }
    /** @param model Jan model name @return configured Jan client */
    public static LLMClient jan(String model) { return create(ClientConfig.of(Provider.JAN).model(model)); }
    /** @param model OpenAI model name @param apiKey OpenAI API key @return configured OpenAI client */
    public static LLMClient openAI(String model, String apiKey) { return create(ClientConfig.of(Provider.OPENAI).model(model).apiKey(apiKey)); }
    /** @param model DeepSeek model name @param apiKey DeepSeek API key @return configured DeepSeek client */
    public static LLMClient deepSeek(String model, String apiKey) { return create(ClientConfig.of(Provider.DEEPSEEK).model(model).apiKey(apiKey)); }
    /** @param model NVIDIA model name @param apiKey NVIDIA API key @return configured NVIDIA client */
    public static LLMClient nvidia(String model, String apiKey) { return create(ClientConfig.of(Provider.NVIDIA).model(model).apiKey(apiKey)); }
    /** @param model OpenRouter model name @param apiKey OpenRouter API key @return configured OpenRouter client */
    public static LLMClient openRouter(String model, String apiKey) { return create(ClientConfig.of(Provider.OPENROUTER).model(model).apiKey(apiKey)); }
    /** @param model Claude model name @param apiKey Anthropic API key @return configured Claude client */
    public static LLMClient claude(String model, String apiKey) { return create(ClientConfig.of(Provider.ANTHROPIC).model(model).apiKey(apiKey)); }
    /** @param model Mistral model name @param apiKey Mistral API key @return configured Mistral client */
    public static LLMClient mistral(String model, String apiKey) { return create(ClientConfig.of(Provider.MISTRAL).model(model).apiKey(apiKey)); }
    /** @param model Groq model name @param apiKey Groq API key @return configured Groq client */
    public static LLMClient groq(String model, String apiKey) { return create(ClientConfig.of(Provider.GROQ).model(model).apiKey(apiKey)); }
    /** @param provider provider enum @return base config for the provider */
    public static ClientConfig config(Provider provider) { return ClientConfig.of(provider); }
}
