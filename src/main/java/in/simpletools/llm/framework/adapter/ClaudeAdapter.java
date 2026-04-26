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
 * Adapter for Anthropic Claude Messages API.
 *
 * <p>The adapter converts framework messages into Claude's message format,
 * handles tool-use content blocks, and parses Claude responses back into
 * provider-neutral {@link LLMResponse} objects.</p>
 */
public class ClaudeAdapter implements ProviderAdapter {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    /**
     * Create a Claude adapter.
     *
     * @param baseUrl Anthropic API base URL
     * @param model Claude model name
     * @param apiKey Anthropic API key
     */
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
        var m = new HashMap<String, Object>();
        m.put("model", model);
        m.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 1024);
        if (request.temperature() != null) m.put("temperature", request.temperature());

        // Extract system message and build messages list
        var systemMsgs = request.messages().stream()
            .filter(msg -> msg.role() == Message.Role.system)
            .map(Message::content)
            .toList();
        String systemPrompt = systemMsgs.isEmpty() ? null : systemMsgs.get(0);

        var msgs = request.messages().stream()
            .filter(msg -> msg.role() != Message.Role.system)
            .map(msg -> {
                var msgMap = new HashMap<String, Object>();
                msgMap.put("role", msg.role().name());
                msgMap.put("content", msg.content());
                return msgMap;
            })
            .collect(Collectors.toList());

        if (systemPrompt != null) m.put("system", systemPrompt);
        m.put("messages", msgs);
        return m;
    }

    private LLMResponse fromClaudeFormat(Map<String, Object> data) {
        var finishReason = data.get("stop_reason") != null ? data.get("stop_reason").toString() : null;
        var usage = parseUsage(data);

        // Extract content blocks (text and tool_use)
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();

        if (data.get("content") instanceof List<?> blocks) {
            for (var block : blocks) {
                if (!(block instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                var b = (Map<String, Object>) block;
                if ("text".equals(b.get("type"))) {
                    String text = (String) b.get("text");
                    if (text != null) textContent.append(text);
                } else if ("tool_use".equals(b.get("type"))) {
                    var tc = parseToolCall(b);
                    toolCalls.add(tc);
                }
            }
        }

        var message = new Message(Message.Role.assistant, textContent.toString());
        if (!toolCalls.isEmpty()) message = message.withToolCalls(toolCalls);

        return new LLMResponse(model, message, finishReason, null, usage, true, null, null, null);
    }

    private ToolCall parseToolCall(Map<String, Object> block) {
        var id = (String) block.get("id");
        var name = (String) block.get("name");
        @SuppressWarnings("unchecked")
        var input = (Map<String, Object>) block.get("input");
        var fn = new ToolCall.Function(name, input != null ? input : Map.of());
        return new ToolCall(id, fn);
    }

    private LLMResponse.Usage parseUsage(Map<String, Object> data) {
        if (!(data.get("usage") instanceof Map)) return new LLMResponse.Usage(0, 0, 0, 0, 0);
        @SuppressWarnings("unchecked")
        var usageMap = (Map<String, Object>) data.get("usage");
        int inputTokens = toInt(usageMap.get("input_tokens"));
        int outputTokens = toInt(usageMap.get("output_tokens"));
        int promptTokens = toInt(usageMap.get("prompt_tokens"));
        int completionTokens = toInt(usageMap.get("completion_tokens"));
        return new LLMResponse.Usage(promptTokens, completionTokens, inputTokens + outputTokens, inputTokens, outputTokens);
    }

    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : 0;
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
        return new LLMResponse(null, new Message(Message.Role.assistant, "Error: " + error), "error", null, null, true, null, null, null);
    }
}
