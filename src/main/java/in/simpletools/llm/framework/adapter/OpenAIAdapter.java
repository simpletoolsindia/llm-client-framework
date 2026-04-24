package in.simpletools.llm.framework.adapter;

import in.simpletools.llm.framework.config.ClientConfig;
import in.simpletools.llm.framework.model.*;
import com.google.gson.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class OpenAIAdapter implements ProviderAdapter {
    protected final ClientConfig config;
    protected final HttpClient httpClient;
    protected final Gson gson = new Gson();

    public OpenAIAdapter(ClientConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        try {
            String json = gson.toJson(request.toMap());
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("API error: " + resp.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            return fromProviderFormat(data);
        } catch (Exception e) {
            return createErrorResponse(e.getMessage());
        }
    }

    @Override
    public void streamChat(LLMRequest request, Consumer<String> onChunk) {
        try {
            Map<String, Object> reqMap = new HashMap<>(request.toMap());
            reqMap.put("stream", true);
            String json = gson.toJson(reqMap);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofMinutes(5))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            for (String line : resp.body().split("\n")) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if (data.equals("[DONE]")) break;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = gson.fromJson(data, Map.class);
                    parseChunk(chunk, onChunk);
                }
            }
        } catch (Exception e) { onChunk.accept("Error: " + e.getMessage()); }
    }

    protected void parseChunk(Map<String, Object> chunk, Consumer<String> onChunk) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
        if (choices != null && !choices.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
            if (delta != null) {
                Object content = delta.get("content");
                if (content != null) onChunk.accept(content.toString());
            }
        }
    }

    @Override
    public String generate(String prompt) {
        try {
            Map<String, Object> req = Map.of("model", config.getModel(), "prompt", prompt, "max_tokens", 1000);
            String json = gson.toJson(req);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
            return choices != null ? (String) choices.get(0).get("text") : "";
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }

    @Override
    public boolean isAvailable() {
        try {
            httpClient.send(HttpRequest.newBuilder().uri(URI.create(config.getBaseUrl())).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public LLMResponse fromProviderFormat(Map<String, Object> data) {
        LLMResponse resp = new LLMResponse();
        resp.setModel((String) data.get("model"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> choice = choices.get(0);
            resp.setFinishReason((String) choice.get("finish_reason"));
            @SuppressWarnings("unchecked")
            Map<String, Object> msgMap = (Map<String, Object>) choice.get("message");
            if (msgMap != null) resp.setMessage(Message.fromMap(msgMap));
        }
        resp.setDone(true);
        return resp;
    }

    protected LLMResponse createErrorResponse(String error) {
        LLMResponse resp = new LLMResponse();
        resp.setMessage(new Message(Message.Role.assistant, "Error: " + error));
        return resp;
    }
}