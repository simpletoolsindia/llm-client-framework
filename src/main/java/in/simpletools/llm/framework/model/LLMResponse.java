package in.simpletools.llm.framework.model;

import java.util.*;

/**
 * Immutable LLM response.
 */
public record LLMResponse(
    String model,
    Message message,
    String finishReason,
    Long totalDuration,
    Usage usage,
    boolean done,
    String id,
    Long created,
    String object
) {
    public LLMResponse {
        if (message == null) message = new Message(Message.Role.assistant, "");
        if (usage == null) usage = new Usage(0, 0, 0, 0, 0);
    }

    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int inputTokens,
        int outputTokens
    ) {}

    public String getContent() { return message.content(); }

    public String getContentOrEmpty() {
        return message.content() != null ? message.content() : "";
    }

    public boolean hasToolCalls() {
        return !message.toolCalls().isEmpty();
    }

    public List<ToolCall> getToolCalls() {
        return hasToolCalls() ? message.toolCalls() : List.of();
    }

    public static LLMResponse fromMap(Map<String, Object> m) {
        var model = (String) m.get("model");
        var finishReason = (String) m.get("finish_reason");
        var done = Boolean.TRUE.equals(m.get("done"));
        Long totalDuration = null;
        if (m.get("total_duration") instanceof Number n) totalDuration = n.longValue();

        var msg = m.get("message") instanceof Map<?, ?> msgMap
            ? Message.fromMap((Map<String, Object>) msgMap)
            : new Message(Message.Role.assistant, "");

        return new LLMResponse(model, msg, finishReason, totalDuration, null, done, null, null, null);
    }
}
