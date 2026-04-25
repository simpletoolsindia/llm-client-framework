package in.simpletools.llm.framework.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable message representation for LLM conversations.
 */
public record Message(
    Role role,
    String content,
    String name,
    List<ToolCall> toolCalls,
    List<ContentPart> contentParts
) {
    public enum Role { system, user, assistant, tool }

    public Message {
        if (toolCalls == null) toolCalls = List.of();
        if (contentParts == null) contentParts = List.of();
    }

    public Message(Role role, String content) {
        this(role, content, null, List.of(), List.of());
    }

    public static Message ofSystem(String content) { return new Message(Role.system, content); }
    public static Message ofUser(String content) { return new Message(Role.user, content); }
    public static Message ofAssistant(String content) { return new Message(Role.assistant, content); }
    public static Message ofTool(String content) { return new Message(Role.tool, content); }

    public static Message ofTool(String content, String toolName) {
        var msg = new Message(Role.tool, content);
        return msg.withName(toolName);
    }

    public Message withName(String name) {
        return new Message(role, content, name, toolCalls, contentParts);
    }

    public Message withToolCalls(List<ToolCall> toolCalls) {
        return new Message(role, content, name, toolCalls, contentParts);
    }

    public Message withContent(String content) {
        return new Message(role, content, name, toolCalls, contentParts);
    }

    public static MessageBuilder user() { return new MessageBuilder(Role.user); }
    public static MessageBuilder assistant() { return new MessageBuilder(Role.assistant); }

    public static class MessageBuilder {
        private Role role;
        private String content;
        private String name;
        private final List<ContentPart> contentParts = new ArrayList<>();

        private MessageBuilder(Role role) { this.role = role; }
        public MessageBuilder text(String content) { this.content = content; return this; }
        public MessageBuilder image(String url) {
            contentParts.add(ContentPart.image(url)); return this;
        }
        public MessageBuilder name(String name) { this.name = name; return this; }
        public Message build() { return new Message(role, content, name, List.of(), List.copyOf(contentParts)); }
    }

    public record ContentPart(String type, String text, String imageUrl) {
        public static ContentPart text(String text) { return new ContentPart("text", text, null); }
        public static ContentPart image(String url) { return new ContentPart("image_url", null, url); }

        public Map<String, Object> toMap() {
            var m = new HashMap<String, Object>();
            m.put("type", type);
            if ("text".equals(type)) m.put("text", text);
            else if ("image_url".equals(type)) m.put("image_url", Map.of("url", imageUrl));
            return m;
        }
    }

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
