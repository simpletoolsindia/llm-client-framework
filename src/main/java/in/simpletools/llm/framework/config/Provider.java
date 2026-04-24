package in.simpletools.llm.framework.config;

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

    public String getDefaultBaseUrl() { return defaultBaseUrl; }
    public String getApiVersion() { return apiVersion; }
    public boolean requiresApiKey() { return requiresApiKey; }
    public boolean isCloud() { return requiresApiKey && !defaultBaseUrl.contains("localhost"); }
    public boolean isLocal() { return !requiresApiKey || defaultBaseUrl.contains("localhost"); }
    public boolean supportsTools() { return this != ANTHROPIC; }
    public boolean supportsVision() {
        return this == OPENAI || this == OLLAMA || this == ANTHROPIC ||
               this == DEEPSEEK || this == NVIDIA || this == OPENROUTER;
    }
}