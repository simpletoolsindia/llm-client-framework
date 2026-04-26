package in.simpletools.llm.framework.adapter;

import in.simpletools.llm.framework.model.*;
import com.google.gson.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Provider adapter for Ollama's native {@code /api/chat} and {@code /api/generate} endpoints.
 *
 * <p>This adapter is selected by {@link in.simpletools.llm.framework.client.LLMClient}
 * when the provider is {@code OLLAMA}. It supports chat messages, tool schemas,
 * streaming chunks, and simple prompt generation.</p>
 */
public class OllamaAdapter implements ProviderAdapter {
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    /**
     * Create an Ollama adapter.
     *
     * @param baseUrl Ollama server base URL, usually {@code http://localhost:11434}
     * @param model model name, for example {@code gemma4:latest}
     */
    public OllamaAdapter(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        try {
            var reqMap = buildRequestMap(request, false);
            String json = gson.toJson(reqMap);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("Ollama API error: " + resp.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return parseResponse(data);
        } catch (Exception e) { return createErrorResponse(e.getMessage()); }
    }

    @Override
    public void streamChat(LLMRequest request, Consumer<String> onChunk) {
        try {
            var reqMap = buildRequestMap(request, true);
            String json = gson.toJson(reqMap);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
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
        } catch (Exception e) { onChunk.accept("Error: " + e.getMessage()); }
    }

    private Map<String, Object> buildRequestMap(LLMRequest request, boolean stream) {
        var reqMap = new HashMap<String, Object>();
        reqMap.put("model", model);
        reqMap.put("stream", stream);
        var messages = request.messages().stream().map(Message::toMap).collect(Collectors.toList());
        reqMap.put("messages", messages);
        if (request.temperature() != null) reqMap.put("temperature", request.temperature());
        if (!request.tools().isEmpty()) {
            var tools = request.tools().stream().map(Tool::toMap).collect(Collectors.toList());
            reqMap.put("tools", tools);
        }
        return reqMap;
    }

    @Override
    public String generate(String prompt) {
        try {
            Map<String, Object> req = Map.of("model", model, "prompt", prompt, "stream", false);
            String json = gson.toJson(req);
            HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return (String) data.get("response");
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    @Override
    public boolean isAvailable() {
        try {
            httpClient.send(HttpRequest.newBuilder().uri(URI.create(baseUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) { return false; }
    }

    private LLMResponse parseResponse(Map<String, Object> data) {
        var model = (String) data.get("model");
        Long totalDuration = data.get("total_duration") instanceof Number n ? n.longValue() : null;

        var usage = parseUsage(data);

        Message message = new Message(Message.Role.assistant, "");
        if (data.get("message") instanceof Map<?, ?> msgMap) {
            message = Message.fromMap((Map<String, Object>) msgMap);

            // Check for tool calls
            if (msgMap.get("tool_calls") instanceof List<?> toolCallsRaw) {
                var toolCalls = toolCallsRaw.stream()
                    .filter(tc -> tc instanceof Map)
                    .map(tc -> parseToolCall((Map<String, Object>) tc))
                    .collect(Collectors.toList());
                message = message.withToolCalls(toolCalls);
            }
        }

        return new LLMResponse(model, message, "stop", totalDuration, usage, true, null, null, null);
    }

    private LLMResponse.Usage parseUsage(Map<String, Object> data) {
        int promptTokens = data.get("prompt_eval_count") instanceof Number n ? n.intValue() : 0;
        int completionTokens = data.get("eval_count") instanceof Number n ? n.intValue() : 0;
        int totalTokens = promptTokens + completionTokens;
        return new LLMResponse.Usage(promptTokens, completionTokens, totalTokens, promptTokens, completionTokens);
    }

    private ToolCall parseToolCall(Map<String, Object> tc) {
        if (tc.get("function") instanceof Map<?, ?> fnMap) {
            var name = (String) fnMap.get("name");
            @SuppressWarnings("unchecked")
            var arguments = (Map<String, Object>) fnMap.get("arguments");
            return new ToolCall(null, new ToolCall.Function(name, arguments));
        }
        return new ToolCall(null, new ToolCall.Function("", Map.of()));
    }

    private LLMResponse createErrorResponse(String error) {
        return new LLMResponse(null, new Message(Message.Role.assistant, "Error: " + error), "error", null, null, true, null, null, null);
    }
}
