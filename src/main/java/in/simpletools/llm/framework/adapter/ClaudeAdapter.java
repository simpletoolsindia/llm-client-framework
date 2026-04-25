package in.simpletools.llm.framework.adapter;

import in.simpletools.llm.framework.model.*;
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
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
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
            if (resp.statusCode() != 200) throw new RuntimeException("Claude API error: " + resp.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return fromClaudeFormat(data);
        } catch (Exception e) { return createErrorResponse(e.getMessage()); }
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
            for (String line : resp.body().split("\n")) {
                if (line.startsWith("data: ")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = gson.fromJson(line.substring(6), Map.class);
                    parseChunk(chunk, onChunk);
                }
            }
        } catch (Exception e) { onChunk.accept("Error: " + e.getMessage()); }
    }

    @Override
    public String generate(String prompt) {
        return chat(LLMRequest.builder().model(model).addMessage(Message.ofUser(prompt)).build()).getContentOrEmpty();
    }

    @Override
    public boolean isAvailable() {
        try {
            httpClient.send(HttpRequest.newBuilder().uri(URI.create(baseUrl.replace("/v1", ""))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) { return false; }
    }

    private Map<String, Object> toClaudeFormat(LLMRequest request) {
        Map<String, Object> m = new HashMap<>();
        m.put("model", model);
        m.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1024);
        if (request.getTemperature() != null) m.put("temperature", request.getTemperature());
        List<Map<String, Object>> msgs = new ArrayList<>();
        if (request.getMessages() != null) {
            for (Message msg : request.getMessages()) {
                if (msg.getRole() == Message.Role.system) m.put("system", msg.getContent());
                else {
                    Map<String, Object> msgMap = new HashMap<>();
                    msgMap.put("role", msg.getRole().name());
                    msgMap.put("content", msg.getContent());
                    msgs.add(msgMap);
                }
            }
        }
        m.put("messages", msgs);
        return m;
    }

    private LLMResponse fromClaudeFormat(Map<String, Object> data) {
        LLMResponse resp = new LLMResponse();
        resp.setModel(model);
        resp.setDone(true);
        Object stop = data.get("stop_reason");
        if (stop != null) resp.setFinishReason(stop.toString());

        // Extract usage data
        Object usageObj = data.get("usage");
        if (usageObj instanceof Map) {
            LLMResponse.Usage usage = new LLMResponse.Usage();
            @SuppressWarnings("unchecked")
            Map<String, Object> usageMap = (Map<String, Object>) usageObj;
            if (usageMap.get("input_tokens") != null)
                usage.setInputTokens(((Number) usageMap.get("input_tokens")).intValue());
            if (usageMap.get("output_tokens") != null)
                usage.setOutputTokens(((Number) usageMap.get("output_tokens")).intValue());
            if (usageMap.get("prompt_tokens") != null)
                usage.setPromptTokens(((Number) usageMap.get("prompt_tokens")).intValue());
            if (usageMap.get("completion_tokens") != null)
                usage.setCompletionTokens(((Number) usageMap.get("completion_tokens")).intValue());
            resp.setUsage(usage);
        }

        // Extract content blocks (text and tool_use)
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();
        Object content = data.get("content");
        if (content instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) content;
            for (Map<String, Object> block : blocks) {
                if ("text".equals(block.get("type"))) {
                    String text = (String) block.get("text");
                    if (text != null) textContent.append(text);
                } else if ("tool_use".equals(block.get("type"))) {
                    ToolCall tc = new ToolCall();
                    tc.setId((String) block.get("id"));
                    ToolCall.Function fn = new ToolCall.Function();
                    fn.setName((String) block.get("name"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = (Map<String, Object>) block.get("input");
                    fn.setArguments(input != null ? input : new java.util.HashMap<>());
                    tc.setFunction(fn);
                    toolCalls.add(tc);
                }
            }
        }

        Message message = new Message(Message.Role.assistant, textContent.toString());
        if (!toolCalls.isEmpty()) message.setToolCalls(toolCalls);
        resp.setMessage(message);
        return resp;
    }

    private void parseChunk(Map<String, Object> chunk, Consumer<String> onChunk) {
        if ("content_block_delta".equals(chunk.get("type"))) {
            Object delta = chunk.get("delta");
            if (delta instanceof Map) {
                Object text = ((Map<?, ?>) delta).get("text");
                if (text != null) onChunk.accept(text.toString());
            }
        }
    }

    private LLMResponse createErrorResponse(String error) {
        LLMResponse resp = new LLMResponse();
        resp.setMessage(new Message(Message.Role.assistant, "Error: " + error));
        return resp;
    }
}