package in.simpletools.llm.framework.config;

/**
 * Supported provider identifiers and their default connection settings.
 *
 * <p>Local providers such as {@link #OLLAMA}, {@link #LM_STUDIO}, {@link #VLLM},
 * and {@link #JAN} default to localhost endpoints and usually do not require
 * API keys. Cloud providers default to their public API endpoints and require
 * API keys.</p>
 *
 * <p>{@link #CUSTOM} is available for OpenAI-compatible gateways or private
 * provider deployments. Use {@link ClientConfig#baseUrl(String)} and
 * {@link ClientConfig#apiKey(String)} to supply the custom endpoint details.</p>
 */
public enum Provider {
    OLLAMA("http://localhost:11434", "v1", false),
    LM_STUDIO("http://localhost:1234/v1", "v1", false),
    VLLM("http://localhost:8000/v1", "v1", false),
    JAN("http://localhost:1337/v1", "v1", false),
    OPENAI("https://api.openai.com/v1", "v1", true),
    DEEPSEEK("https://api.deepseek.com/v1", "v1", true),
    NVIDIA("https://integrate.api.nvidia.com/v1", "v1", true),
    OPENROUTER("https://openrouter.ai/api/v1", "v1", true),
    GROQ("https://api.groq.com/openai/v1", "v1", true),
    MISTRAL("https://api.mistral.ai/v1", "v1", true),
    ANTHROPIC("https://api.anthropic.com/v1", "v1", true),
    CUSTOM("", "v1", true);

    private final String defaultBaseUrl;
    private final String apiVersion;
    private final boolean requiresApiKey;

    Provider(String defaultBaseUrl, String apiVersion, boolean requiresApiKey) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.apiVersion = apiVersion;
        this.requiresApiKey = requiresApiKey;
    }

    /** @return default API base URL for this provider */
    public String getDefaultBaseUrl() { return defaultBaseUrl; }
    /** @return API version label used by the framework for this provider */
    public String getApiVersion() { return apiVersion; }
    /** @return true when this provider normally requires an API key */
    public boolean requiresApiKey() { return requiresApiKey; }
    /** @return true for non-local providers that normally require API keys */
    public boolean isCloud() { return requiresApiKey && !defaultBaseUrl.contains("localhost"); }
    /** @return true for local providers or localhost-compatible endpoints */
    public boolean isLocal() { return !requiresApiKey || defaultBaseUrl.contains("localhost"); }
    /** @return true when the framework exposes tool schemas for this provider */
    public boolean supportsTools() { return true; }
    /** @return true when the provider family supports image content in chat messages */
    public boolean supportsVision() {
        return this == OPENAI || this == OLLAMA || this == ANTHROPIC ||
               this == DEEPSEEK || this == NVIDIA || this == OPENROUTER;
    }
}
