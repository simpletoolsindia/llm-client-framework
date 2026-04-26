package in.simpletools.llm.framework.adapter;

import in.simpletools.llm.framework.model.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter contract for provider-specific LLM integrations.
 *
 * <p>An adapter translates {@link LLMRequest} into provider HTTP/API calls and
 * parses provider responses back into {@link LLMResponse}. Implement this when
 * adding a new provider, private gateway, or mock provider for tests.</p>
 */
public interface ProviderAdapter {
    /**
     * Send a non-streaming chat request.
     *
     * @param request provider-neutral request
     * @return provider-neutral response; implementations should return an error response rather than throwing for API failures
     */
    LLMResponse chat(LLMRequest request);
    /**
     * Send a streaming chat request.
     *
     * @param request provider-neutral request
     * @param onChunk callback invoked with text chunks as they arrive
     */
    void streamChat(LLMRequest request, Consumer<String> onChunk);
    /**
     * Send a plain text generation request.
     *
     * @param prompt plain text generation prompt
     * @return generated text or error string
     */
    String generate(String prompt);
    /**
     * Check whether the provider endpoint is reachable.
     *
     * @return true when the provider endpoint appears reachable
     */
    boolean isAvailable();
    /**
     * Return default HTTP headers for this adapter.
     *
     * @return default HTTP headers used by this adapter, if any
     */
    default Map<String, String> getHeaders() { return Map.of(); }
    /**
     * Parse a provider response map into the framework response model.
     *
     * @param response provider response map
     * @return parsed response
     */
    default LLMResponse fromProviderFormat(Map<String, Object> response) { return LLMResponse.fromMap(response); }
}
