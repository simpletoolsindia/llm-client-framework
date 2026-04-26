package in.simpletools.llm.framework.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable message representation for provider-neutral chat conversations.
 *
 * <p>A message has a {@link Role}, optional text content, an optional name, zero
 * or more tool calls, and optional multimodal content parts. The client keeps
 * these messages in {@link in.simpletools.llm.framework.history.ConversationHistory}
 * and adapters convert them into provider-specific JSON.</p>
 *
 * @param role message role: system, user, assistant, or tool
 * @param content plain text content; may be empty for assistant tool-call messages
 * @param name optional name, commonly used for tool result messages
 * @param toolCalls tool calls requested by an assistant message
 * @param contentParts optional structured content parts such as text and image URLs
 */
public record Message(
    Role role,
    String content,
    String name,
    List<ToolCall> toolCalls,
    List<ContentPart> contentParts
) {
    /**
     * Chat role names used by provider APIs.
     */
    public enum Role { system, user, assistant, tool }

    public Message {
        if (toolCalls == null) toolCalls = List.of();
        if (contentParts == null) contentParts = List.of();
    }

    /**
     * Create a text-only message.
     *
     * @param role message role
     * @param content text content
     */
    public Message(Role role, String content) {
        this(role, content, null, List.of(), List.of());
    }

    /** @param content system instructions @return system message */
    public static Message ofSystem(String content) { return new Message(Role.system, content); }
    /** @param content user prompt @return user message */
    public static Message ofUser(String content) { return new Message(Role.user, content); }
    /** @param content assistant text @return assistant message */
    public static Message ofAssistant(String content) { return new Message(Role.assistant, content); }
    /** @param content tool result content @return tool message */
    public static Message ofTool(String content) { return new Message(Role.tool, content); }

    /**
     * Create a named tool result message.
     *
     * @param content tool result content
     * @param toolName name of the tool that produced the result
     * @return tool message with {@code name} set
     */
    public static Message ofTool(String content, String toolName) {
        var msg = new Message(Role.tool, content);
        return msg.withName(toolName);
    }

    /** @param name new message name @return copy with the supplied name */
    public Message withName(String name) {
        return new Message(role, content, name, toolCalls, contentParts);
    }

    /** @param toolCalls tool calls to attach @return copy with the supplied tool calls */
    public Message withToolCalls(List<ToolCall> toolCalls) {
        return new Message(role, content, name, toolCalls, contentParts);
    }

    /** @param content replacement text content @return copy with the supplied content */
    public Message withContent(String content) {
        return new Message(role, content, name, toolCalls, contentParts);
    }

    /** @return builder preconfigured for a user message */
    public static MessageBuilder user() { return new MessageBuilder(Role.user); }
    /** @return builder preconfigured for an assistant message */
    public static MessageBuilder assistant() { return new MessageBuilder(Role.assistant); }

    /**
     * Fluent builder for text and image messages.
     */
    public static class MessageBuilder {
        private Role role;
        private String content;
        private String name;
        private final List<ContentPart> contentParts = new ArrayList<>();

        private MessageBuilder(Role role) { this.role = role; }
        /** @param content text content @return this builder */
        public MessageBuilder text(String content) { this.content = content; return this; }
        /** @param url image URL or data URL @return this builder */
        public MessageBuilder image(String url) {
            contentParts.add(ContentPart.image(url)); return this;
        }
        /** @param name optional message name @return this builder */
        public MessageBuilder name(String name) { this.name = name; return this; }
        /** @return immutable message */
        public Message build() { return new Message(role, content, name, List.of(), List.copyOf(contentParts)); }
    }

    /**
     * Structured content part used for multimodal messages.
     *
     * @param type provider-neutral part type, such as {@code text} or {@code image_url}
     * @param text text content when {@code type=text}
     * @param imageUrl image URL when {@code type=image_url}
     */
    public record ContentPart(String type, String text, String imageUrl) {
        /** @param text text content @return text content part */
        public static ContentPart text(String text) { return new ContentPart("text", text, null); }
        /** @param url image URL or data URL @return image content part */
        public static ContentPart image(String url) { return new ContentPart("image_url", null, url); }

        /** @return provider-style map representation */
        public Map<String, Object> toMap() {
            var m = new HashMap<String, Object>();
            m.put("type", type);
            if ("text".equals(type)) m.put("text", text);
            else if ("image_url".equals(type)) m.put("image_url", Map.of("url", imageUrl));
            return m;
        }
    }

    /** @return provider-style map representation */
    public Map<String, Object> toMap() {
        var m = new HashMap<String, Object>();
        m.put("role", role.name());
        if (!contentParts.isEmpty()) {
            m.put("content", contentParts.stream().map(ContentPart::toMap).collect(Collectors.toList()));
        } else {
            m.put("content", content);
        }
        if (name != null) m.put("name", name);
        if (!toolCalls.isEmpty()) {
            m.put("tool_calls", toolCalls.stream().map(ToolCall::toMap).collect(Collectors.toList()));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    /**
     * Parse a message from a provider-style map.
     *
     * @param m map containing at least a {@code role} key
     * @return parsed message
     */
    public static Message fromMap(Map<String, Object> m) {
        var role = Role.valueOf((String) m.get("role"));
        var content = (String) m.get("content");
        var name = (String) m.get("name");
        var calls = m.get("tool_calls");
        List<ToolCall> toolCalls = List.of();
        if (calls instanceof List<?>) {
            toolCalls = ((List<?>) calls).stream()
                .filter(c -> c instanceof Map)
                .map(c -> ToolCall.fromMap((Map<String, Object>) c))
                .collect(Collectors.toList());
        }
        return new Message(role, content, name, toolCalls, List.of());
    }
}
