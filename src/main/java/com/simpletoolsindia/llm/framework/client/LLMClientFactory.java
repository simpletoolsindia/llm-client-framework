package com.simpletoolsindia.llm.framework.client;

import com.simpletoolsindia.llm.framework.config.*;
import java.util.*;
import java.util.function.Function;

public class LLMClientFactory {

    public static LLMClient create(Provider provider, String baseUrl, String model, String apiKey) {
        ClientConfig config = ClientConfig.of(provider)
            .baseUrl(baseUrl)
            .model(model)
            .apiKey(apiKey);
        return LLMClient.create(config);
    }

    public static LLMClient create(String provider, String baseUrl, String model, String apiKey) {
        return create(Provider.valueOf(provider.toUpperCase()), baseUrl, model, apiKey);
    }

    public static LLMClient ollama() {
        return create(ClientConfig.of(Provider.OLLAMA));
    }

    public static LLMClient ollama(String model) {
        return create(ClientConfig.of(Provider.OLLAMA).model(model));
    }

    public static LLMClient ollama(String baseUrl, String model) {
        return create(ClientConfig.of(Provider.OLLAMA).baseUrl(baseUrl).model(model));
    }

    public static LLMClient lmStudio() {
        return create(ClientConfig.of(Provider.LM_STUDIO));
    }

    public static LLMClient lmStudio(String model) {
        return create(ClientConfig.of(Provider.LM_STUDIO).model(model));
    }

    public static LLMClient vllm() {
        return create(ClientConfig.of(Provider.VLLM));
    }

    public static LLMClient vllm(String model) {
        return create(ClientConfig.of(Provider.VLLM).model(model));
    }

    public static LLMClient jan() {
        return create(ClientConfig.of(Provider.JAN));
    }

    public static LLMClient jan(String model) {
        return create(ClientConfig.of(Provider.JAN).model(model));
    }

    public static LLMClient openAI(String model, String apiKey) {
        return create(ClientConfig.of(Provider.OPENAI).model(model).apiKey(apiKey));
    }

    public static LLMClient deepSeek(String model, String apiKey) {
        return create(ClientConfig.of(Provider.DEEPSEEK).model(model).apiKey(apiKey));
    }

    public static LLMClient nvidia(String model, String apiKey) {
        return create(ClientConfig.of(Provider.NVIDIA).model(model).apiKey(apiKey));
    }

    public static LLMClient openRouter(String model, String apiKey) {
        return create(ClientConfig.of(Provider.OPENROUTER).model(model).apiKey(apiKey));
    }

    public static LLMClient claude(String model, String apiKey) {
        return create(ClientConfig.of(Provider.ANTHROPIC).model(model).apiKey(apiKey));
    }

    public static LLMClient mistral(String model, String apiKey) {
        return create(ClientConfig.of(Provider.MISTRAL).model(model).apiKey(apiKey));
    }

    public static LLMClient groq(String model, String apiKey) {
        return create(ClientConfig.of(Provider.GROQ).model(model).apiKey(apiKey));
    }

    public static LLMClient create(ClientConfig config) {
        return LLMClient.create(config);
    }

    public static ClientConfig config(Provider provider) {
        return ClientConfig.of(provider);
    }
}