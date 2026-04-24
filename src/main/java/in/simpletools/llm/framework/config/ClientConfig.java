package in.simpletools.llm.framework.config;

public class ClientConfig {
    private Provider provider;
    private String baseUrl;
    private String model;
    private String apiKey;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private String[] stopSequences;
    private Double timeout;
    private boolean stream = true;

    public ClientConfig() {}

    public static ClientConfig of(Provider provider) {
        ClientConfig c = new ClientConfig();
        c.provider = provider;
        c.baseUrl = provider.getDefaultBaseUrl();
        return c;
    }

    public Provider getProvider() { return provider; }
    public ClientConfig provider(Provider p) { this.provider = p; return this; }

    public String getBaseUrl() { return baseUrl; }
    public ClientConfig baseUrl(String url) { this.baseUrl = url; return this; }

    public String getModel() { return model; }
    public ClientConfig model(String m) { this.model = m; return this; }

    public String getApiKey() { return apiKey; }
    public ClientConfig apiKey(String key) { this.apiKey = key; return this; }

    public Double getTemperature() { return temperature; }
    public ClientConfig temperature(Double t) { this.temperature = t; return this; }

    public Integer getMaxTokens() { return maxTokens; }
    public ClientConfig maxTokens(Integer m) { this.maxTokens = m; return this; }

    public Double getTopP() { return topP; }
    public ClientConfig topP(Double p) { this.topP = p; return this; }

    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public ClientConfig frequencyPenalty(Double p) { this.frequencyPenalty = p; return this; }

    public Double getPresencePenalty() { return presencePenalty; }
    public ClientConfig presencePenalty(Double p) { this.presencePenalty = p; return this; }

    public String[] getStopSequences() { return stopSequences; }
    public ClientConfig stop(String... s) { this.stopSequences = s; return this; }

    public Double getTimeout() { return timeout; }
    public ClientConfig timeout(Double seconds) { this.timeout = seconds; return this; }

    public boolean isStream() { return stream; }
    public ClientConfig stream(boolean s) { this.stream = s; return this; }

    public ClientConfig ollama(String baseUrl, String model) {
        return provider(Provider.OLLAMA).baseUrl(baseUrl).model(model);
    }

    public ClientConfig openAI(String model, String apiKey) {
        return provider(Provider.OPENAI).model(model).apiKey(apiKey);
    }

    public ClientConfig claude(String model, String apiKey) {
        return provider(Provider.ANTHROPIC).model(model).apiKey(apiKey);
    }

    public ClientConfig deepSeek(String model, String apiKey) {
        return provider(Provider.DEEPSEEK).model(model).apiKey(apiKey);
    }

    public ClientConfig nvidia(String model, String apiKey) {
        return provider(Provider.NVIDIA).model(model).apiKey(apiKey);
    }

    public ClientConfig openRouter(String model, String apiKey) {
        return provider(Provider.OPENROUTER).model(model).apiKey(apiKey);
    }

    public ClientConfig lmStudio(String model) {
        return provider(Provider.LM_STUDIO).model(model);
    }

    public ClientConfig vllm(String model) {
        return provider(Provider.VLLM).model(model);
    }
}