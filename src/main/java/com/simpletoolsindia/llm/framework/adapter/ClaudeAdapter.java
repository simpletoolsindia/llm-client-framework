package com.simpletoolsindia.llm.framework.adapter;

import com.simpletoolsindia.llm.framework.model.*;
import com.google.gson.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class ClaudeAdapter implements ProviderAdapter {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public ClaudeAdapter(String baseUrl, String model, String apiKey) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        try {
            Map<String, Object> reqMap = toClaudeFormat(request);
            String json = gson.toJson(reqMap);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-dangerous-direct-browser-access", "true")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new RuntimeException("Claude API error: " + resp.body());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return fromClaudeFormat(data);
        } catch (Exception e) {
            return createErrorResponse(e.getMessage());
        }
    }

    @Override
    public void streamChat(LLMRequest request, Consumer<String> onChunk) {
        try {
            Map<String, Object> reqMap = toClaudeFormat(request);
            reqMap.put("stream", true);
            String json = gson.toJson(reqMap);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-dangerous-direct-browser-access", "true")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new RuntimeException("Claude API error");

            for (String line : resp.body().split("\n")) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = gson.fromJson(data, Map.class);
                    parseChunk(chunk, onChunk);
                }
            }
        } catch (Exception e) {
            onChunk.accept("Error: " + e.getMessage());
        }
    }

    @Override
    public String generate(String prompt) {
        return chat(LLMRequest.builder()
            .model(model)
            .addMessage(Message.ofUser(prompt))
            .build()).getContentOrEmpty();
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replace("/v1", "")))
                .GET().build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) { return false; }
    }

    private Map<String, Object> toClaudeFormat(LLMRequest request) {
        Map<String, Object> m = new HashMap<>();
        m.put("model", model);
        m.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1024);

        if (request.getTemperature() != null)
            m.put("temperature", request.getTemperature());

        List<Map<String, Object>> msgs = new ArrayList<>();
        if (request.getMessages() != null) {
            for (Message msg : request.getMessages()) {
                if (msg.getRole() == Message.Role.system) {
                    m.put("system", msg.getContent());
                } else {
                    Map<String, Object> msgMap = new HashMap<>();
                    msgMap.put("role", msg.getRole().name());
                    msgMap.put("content", msg.getContent());
                    msgs.add(msgMap);
                }
            }
        }
        m.put("messages", msgs);

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (Tool tool : request.getTools()) {
                Map<String, Object> t = new HashMap<>();
                Map<String, Object> inp = new HashMap<>();
                inp.put("name", tool.getFunction().getName());
                inp.put("description", tool.getFunction().getDescription());
                inp.put("input_schema", tool.getFunction().toMap());
                t.put("name", tool.getFunction().getName());
                t.put("description", tool.getFunction().getDescription());
                t.put("input_schema", tool.getFunction().toMap());
                tools.add(t);
            }
            m.put("tools", tools);
        }

        return m;
    }

    private LLMResponse fromClaudeFormat(Map<String, Object> data) {
        LLMResponse resp = new LLMResponse();
        resp.setModel(model);
        resp.setDone(true);

        Object stop = data.get("stop_reason");
        if (stop != null) resp.setFinishReason(stop.toString());

        Object content = data.get("content");
        if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;
            for (Map<String, Object> block : blocks) {
                String type = (String) block.get("type");
                if ("text".equals(type)) {
                    Message msg = new Message(Message.Role.assistant,
                        (String) block.get("text"));
                    resp.setMessage(msg);
                }
            }
        }

        Object usage = data.get("usage");
        if (usage instanceof Map) {
            LLMResponse.Usage u = new LLMResponse.Usage();
            Map<String, Object> um = (Map<String, Object>) usage;
            u.setInputTokens(numInt(um.get("input_tokens")));
            u.setOutputTokens(numInt(um.get("output_tokens")));
            resp.setUsage(u);
        }

        return resp;
    }

    private void parseChunk(Map<String, Object> chunk, Consumer<String> onChunk) {
        String type = (String) chunk.get("type");
        if ("content_block_delta".equals(type)) {
            Object delta = chunk.get("delta");
            if (delta instanceof Map) {
                Object text = ((Map<?, ?>) delta).get("text");
                if (text != null) onChunk.accept(text.toString());
            }
        }
    }

    private int numInt(Object v) { return v instanceof Number ? ((Number) v).intValue() : 0; }

    private LLMResponse createErrorResponse(String error) {
        LLMResponse resp = new LLMResponse();
        Message msg = new Message(Message.Role.assistant, "Error: " + error);
        resp.setMessage(msg);
        return resp;
    }
}
