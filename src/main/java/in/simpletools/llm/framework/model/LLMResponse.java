package in.simpletools.llm.framework.model;

import java.util.*;

/**
 * Immutable provider-neutral LLM response.
 *
 * <p>The response wraps the assistant {@link Message}, token usage, finish
 * reason, and provider metadata. Tool calls are stored on the message and can be
 * inspected with {@link #hasToolCalls()} and {@link #getToolCalls()}.</p>
 *
 * @param model model that produced the response
 * @param message assistant message
 * @param finishReason provider finish reason, such as {@code stop} or {@code error}
 * @param totalDuration provider duration in nanoseconds or milliseconds depending on provider
 * @param usage token usage information
 * @param done true when provider marked the response complete
 * @param id provider response id, when available
 * @param created provider creation timestamp, when available
 * @param object provider object type, when available
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

    /**
     * Token usage summary.
     *
     * @param promptTokens prompt/input tokens
     * @param completionTokens completion/output tokens
     * @param totalTokens total tokens
     * @param inputTokens provider input token alias
     * @param outputTokens provider output token alias
     */
    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int inputTokens,
        int outputTokens
    ) {}

    /** @return assistant message content, possibly null */
    public String getContent() { return message.content(); }

    /** @return assistant message content, or an empty string when null */
    public String getContentOrEmpty() {
        return message.content() != null ? message.content() : "";
    }

    /** @return true when the assistant message requested one or more tool calls */
    public boolean hasToolCalls() {
        return !message.toolCalls().isEmpty();
    }

    /** @return tool calls requested by the assistant, or an empty list */
    public List<ToolCall> getToolCalls() {
        return hasToolCalls() ? message.toolCalls() : List.of();
    }

    /**
     * Parse an OpenAI/Ollama-style response map.
     *
     * @param m provider response map
     * @return parsed response
     */
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
