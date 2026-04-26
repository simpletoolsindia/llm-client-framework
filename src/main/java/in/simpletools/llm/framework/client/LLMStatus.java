package in.simpletools.llm.framework.client;

import java.util.Map;

/**
 * Live status event emitted by {@link LLMClient} while a chat request is running.
 *
 * <p>Status events are designed for CLI, IDE, and agent-style applications that
 * need to show what the framework is doing before the final assistant answer is
 * available. For example, a Claude Code style interface can display when the
 * model starts, when it requests a tool, which tool arguments were supplied,
 * when tool execution starts, when the tool result is validated and appended,
 * when the model continues after tools, and when streaming text arrives.</p>
 *
 * <pre>{@code
 * client.onStatus(status -> {
 *     if (status.type() == LLMStatus.Type.TOOL_EXECUTION_STARTED) {
 *         System.out.println("Running " + status.toolName());
 *     }
 * });
 * }</pre>
 *
 * @param type machine-readable event type
 * @param message short human-readable status message
 * @param toolName tool name for tool-related events, otherwise {@code null}
 * @param arguments tool arguments for tool-related events, otherwise an empty map
 * @param result tool result or streamed chunk for result-related events, otherwise {@code null}
 * @param error exception for error events, otherwise {@code null}
 * @param timestampMillis event creation time in epoch milliseconds
 */
public record LLMStatus(
    Type type,
    String message,
    String toolName,
    Map<String, Object> arguments,
    String result,
    Throwable error,
    long timestampMillis
) {
    /**
     * Known lifecycle stages for chat, streaming, and tool execution.
     */
    public enum Type {
        CHAT_STARTED,
        REQUEST_SENT,
        RESPONSE_RECEIVED,
        STREAM_STARTED,
        STREAM_CHUNK,
        STREAM_COMPLETED,
        TOOL_CALL_REQUESTED,
        TOOL_EXECUTION_STARTED,
        TOOL_EXECUTION_COMPLETED,
        TOOL_EXECUTION_FAILED,
        TOOL_RESPONSE_VALIDATED,
        TOOL_RESPONSE_APPENDED,
        CONTINUATION_STARTED,
        CHAT_COMPLETED,
        ERROR
    }

    public LLMStatus {
        if (arguments == null) arguments = Map.of();
        timestampMillis = timestampMillis > 0 ? timestampMillis : System.currentTimeMillis();
    }

    /** @param type event type @param message human-readable message @return status event */
    public static LLMStatus of(Type type, String message) {
        return new LLMStatus(type, message, null, Map.of(), null, null, System.currentTimeMillis());
    }

    /** @param type event type @param toolName tool name @param arguments tool arguments @return tool status event */
    public static LLMStatus tool(Type type, String toolName, Map<String, Object> arguments) {
        return new LLMStatus(type, toolName, toolName, arguments, null, null, System.currentTimeMillis());
    }

    /** @param type event type @param toolName tool name @param arguments tool arguments @param result result text @return tool status event */
    public static LLMStatus toolResult(Type type, String toolName, Map<String, Object> arguments, String result) {
        return new LLMStatus(type, toolName, toolName, arguments, result, null, System.currentTimeMillis());
    }

    /** @param type event type @param message human-readable message @param result streamed chunk or result @return status event */
    public static LLMStatus result(Type type, String message, String result) {
        return new LLMStatus(type, message, null, Map.of(), result, null, System.currentTimeMillis());
    }

    /** @param message human-readable message @param error exception or failure cause @return error status event */
    public static LLMStatus error(String message, Throwable error) {
        return new LLMStatus(Type.ERROR, message, null, Map.of(), null, error, System.currentTimeMillis());
    }
}
