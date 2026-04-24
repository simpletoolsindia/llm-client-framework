package com.simpletoolsindia.llm.framework.adapter;

import com.simpletoolsindia.llm.framework.config.ClientConfig;
import com.simpletoolsindia.llm.framework.model.*;

import java.util.Map;
import java.util.function.Consumer;

public interface ProviderAdapter {
    LLMResponse chat(LLMRequest request);
    void streamChat(LLMRequest request, Consumer<String> onChunk);
    String generate(String prompt);
    boolean isAvailable();

    default Map<String, String> getHeaders() { return Map.of(); }
    default Map<String, Object> toProviderFormat(LLMRequest request) { return request.toMap(); }
    default LLMResponse fromProviderFormat(Map<String, Object> response) { return LLMResponse.fromMap(response); }
}
