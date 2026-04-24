package com.simpletoolsindia.llm.framework.client;

import com.simpletoolsindia.llm.framework.config.*;
import com.simpletoolsindia.llm.framework.adapter.*;
import com.simpletoolsindia.llm.framework.model.*;
import com.simpletoolsindia.llm.framework.tool.*;
import com.simpletoolsindia.llm.framework.history.ConversationHistory;
import java.util.*;
import java.util.function.Consumer;

public class LLMClient {
    private final ProviderAdapter adapter;
    private final ClientConfig config;
    private final ConversationHistory history;
    private final ToolRegistry toolRegistry;
    private final List<Tool> tools = new ArrayList<>();

    public LLMClient(ProviderAdapter adapter, ClientConfig config) {
        this(adapter, config, new ConversationHistory());
    }

    public LLMClient(ProviderAdapter adapter, ClientConfig config, ConversationHistory history) {
        this.adapter = adapter;
        this.config = config;
        this.history = history;
        this.toolRegistry = new ToolRegistry();
    }

    // ========== Chat Methods ==========

    public String chat(String message) {
        return chat(message, Map.of());
    }

    public String chat(String message, Map<String, String> options) {
        history.addUser(message);

        LLMRequest request = buildRequest(message, options);
        LLMResponse response = adapter.chat(request);

        if (response.hasToolCalls()) {
            return handleToolCalls(response.getToolCalls());
        }

        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        return reply;
    }

    public String chat(Message message) {
        history.add(message);

        LLMRequest request = LLMRequest.builder()
            .model(config.getModel())
            .messages(history.getMessages())
            .tools(tools.isEmpty() ? null : tools)
            .build();

        LLMResponse response = adapter.chat(request);
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        return reply;
    }

    public void streamChat(String message, Consumer<String> onChunk) {
        history.addUser(message);

        LLMRequest request = LLMRequest.builder()
            .model(config.getModel())
            .messages(history.getMessages())
            .stream(true)
            .build();

        adapter.streamChat(request, onChunk);
    }

    public String chatWithTools(String message) {
        return chat(message, Map.of("tools", "true"));
    }

    // ========== Tool Methods ==========

    public LLMClient registerTool(Object service) {
        toolRegistry.register(service);
        for (ToolRegistry.ToolInfo ti : toolRegistry.getAllTools()) {
            tools.add(toolFromInfo(ti));
        }
        return this;
    }

    public LLMClient registerTool(String name, String description,
                                   java.util.function.Function<Map<String, Object>, Object> handler,
                                   Map<String, ToolRegistry.ParamInfo> params) {
        toolRegistry.register(name, description, handler, params);
        tools.add(toolFromInfo(toolRegistry.get(name)));
        return this;
    }

    public LLMClient withTools(List<Tool> tools) {
        this.tools.clear();
        this.tools.addAll(tools);
        return this;
    }

    // ========== History Methods ==========

    public ConversationHistory getHistory() { return history; }
    public void clearHistory() { history.clear(); }
    public void clearLastN(int n) { history.clearLastN(n); }

    // ========== Private Methods ==========

    private LLMRequest buildRequest(String message, Map<String, String> options) {
        LLMRequest.Builder builder = LLMRequest.builder()
            .model(config.getModel())
            .stream(config.isStream())
            .messages(new ArrayList<>(history.getMessages()))
            .addMessage(Message.ofUser(message));

        if (options.containsKey("system")) {
            builder.system(options.get("system"));
        }
        if (options.containsKey("temperature")) {
            builder.temperature(Double.parseDouble(options.get("temperature")));
        }

        if (options.containsKey("tools") || !tools.isEmpty()) {
            builder.tools(tools.isEmpty() ? null : tools);
        }

        return builder.build();
    }

    private String handleToolCalls(List<ToolCall> calls) {
        List<Message> messages = new ArrayList<>(history.getMessages());

        for (ToolCall call : calls) {
            String toolName = call.getFunction().getName();
            Map<String, Object> args = call.getFunction().getArguments();

            Object result;
            try {
                ToolRegistry.ToolInfo info = toolRegistry.get(toolName);
                if (info != null) {
                    result = info.invoke(args);
                } else {
                    result = "Tool not found: " + toolName;
                }
            } catch (Exception e) {
                result = "Error: " + e.getMessage();
            }

            Message toolMsg = Message.ofTool(new com.google.gson.Gson().toJson(result));
            toolMsg.setName(toolName);
            messages.add(toolMsg);
            history.add(toolMsg);
        }

        LLMRequest request = LLMRequest.builder()
            .model(config.getModel())
            .messages(messages)
            .tools(tools)
            .build();

        LLMResponse response = adapter.chat(request);
        String reply = response.getContentOrEmpty();
        history.addAssistant(reply);
        return reply;
    }

    private Tool toolFromInfo(ToolRegistry.ToolInfo info) {
        Tool tool = new Tool();
        Tool.Function fn = new Tool.Function();
        fn.setName(info.getName());
        fn.setDescription(info.getDescription());
        fn.setParameters(new HashMap<>());
        info.getParams().forEach((k, v) -> {
            Tool.Function.Param p = new Tool.Function.Param();
            Class<?> t = v.getType();
            String typeName = t == String.class ? "string" :
                              t == int.class || t == Integer.class || t == long.class || t == Long.class ? "integer" :
                              t == double.class || t == Double.class || t == float.class || t == Float.class ? "number" :
                              t == boolean.class || t == Boolean.class ? "boolean" : "string";
            p.setType(typeName);
            p.setDescription(v.getDescription());
            fn.getParameters().put(k, p);
        });
        tool.setFunction(fn);
        return tool;
    }

    // ========== Static Factory ==========

    public static LLMClient create(ClientConfig config) {
        ProviderAdapter adapter;
        switch (config.getProvider()) {
            case OLLAMA:
                adapter = new OllamaAdapter(
                    config.getBaseUrl(),
                    config.getModel()
                );
                break;
            case ANTHROPIC:
                adapter = new ClaudeAdapter(
                    config.getBaseUrl(),
                    config.getModel(),
                    config.getApiKey()
                );
                break;
            default:
                adapter = new OpenAIAdapter(config);
        }
        return new LLMClient(adapter, config);
    }

    public static LLMClient ollama(String model) {
        return create(ClientConfig.of(Provider.OLLAMA).model(model));
    }

    public static LLMClient openAI(String model, String apiKey) {
        return create(ClientConfig.of(Provider.OPENAI).model(model).apiKey(apiKey));
    }

    public static LLMClient deepSeek(String model, String apiKey) {
        return create(ClientConfig.of(Provider.DEEPSEEK).model(model).apiKey(apiKey));
    }

    public static LLMClient claude(String model, String apiKey) {
        return create(ClientConfig.of(Provider.ANTHROPIC).model(model).apiKey(apiKey));
    }

    public static LLMClient nvidia(String model, String apiKey) {
        return create(ClientConfig.of(Provider.NVIDIA).model(model).apiKey(apiKey));
    }

    public static LLMClient openRouter(String model, String apiKey) {
        return create(ClientConfig.of(Provider.OPENROUTER).model(model).apiKey(apiKey));
    }

    public static LLMClient lmStudio(String model) {
        return create(ClientConfig.of(Provider.LM_STUDIO).model(model));
    }

    public static LLMClient vllm(String model) {
        return create(ClientConfig.of(Provider.VLLM).model(model));
    }

    public static LLMClient jan(String model) {
        return create(ClientConfig.of(Provider.JAN).model(model));
    }
}