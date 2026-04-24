package com.simpletoolsindia.llm.framework.adapter;

import com.simpletoolsindia.llm.framework.config.Provider;
import com.simpletoolsindia.llm.framework.model.*;
import com.google.gson.*;

import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class OllamaAdapter implements ProviderAdapter {
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public OllamaAdapter(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        try {
            Map<String, Object> reqMap = new HashMap<>();
            reqMap.put("model", model);
            reqMap.put("stream", false);

            List<Map<String, Object>> messages = new ArrayList<>();
            if (request.getMessages() != null) {
                request.getMessages().forEach(msg -> messages.add(msg.toMap()));
            }
            reqMap.put("messages", messages);

            if (request.getTemperature() != null)
                reqMap.put("temperature", request.getTemperature());
            if (request.getTools() != null) {
                List<Map<String, Object>> tools = new ArrayList<>();
                request.getTools().forEach(t -> tools.add(t.toMap()));
                reqMap.put("tools", tools);
            }

            String json = gson.toJson(reqMap);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new RuntimeException("Ollama API error: " + resp.body());

            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return parseResponse(data);
        } catch (Exception e) {
            return createErrorResponse(e.getMessage());
        }
    }

    @Override
    public void streamChat(LLMRequest request, Consumer<String> onChunk) {
        try {
            Map<String, Object> reqMap = new HashMap<>();
            reqMap.put("model", model);
            reqMap.put("stream", true);

            List<Map<String, Object>> messages = new ArrayList<>();
            if (request.getMessages() != null) {
                request.getMessages().forEach(msg -> messages.add(msg.toMap()));
            }
            reqMap.put("messages", messages);

            String json = gson.toJson(reqMap);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new RuntimeException("Ollama API error");

            for (String line : resp.body().split("\n")) {
                if (line.isEmpty()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> chunk = gson.fromJson(line, Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = (Map<String, Object>) chunk.get("message");
                if (msg != null) {
                    Object content = msg.get("content");
                    if (content != null) onChunk.accept(content.toString());
                }
            }
        } catch (Exception e) {
            onChunk.accept("Error: " + e.getMessage());
        }
    }

    @Override
    public String generate(String prompt) {
        try {
            Map<String, Object> req = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
            );
            String json = gson.toJson(req);

            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return (String) data.get("response");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .GET().build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) { return false; }
    }

    private LLMResponse parseResponse(Map<String, Object> data) {
        LLMResponse resp = new LLMResponse();
        resp.setModel((String) data.get("model"));
        resp.setDone(true);

        if (data.get("total_duration") != null)
            resp.setTotalDuration(((Number) data.get("total_duration")).longValue());
        if (data.get("done_reason") != null)
            resp.setFinishReason((String) data.get("done_reason"));

        Object msg = data.get("message");
        if (msg instanceof Map) resp.setMessage(Message.fromMap((Map<String, Object>) msg));

        return resp;
    }

    private LLMResponse createErrorResponse(String error) {
        LLMResponse resp = new LLMResponse();
        Message msg = new Message(Message.Role.assistant, "Error: " + error);
        resp.setMessage(msg);
        return resp;
    }
}
