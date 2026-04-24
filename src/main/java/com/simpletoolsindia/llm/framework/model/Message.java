package com.simpletoolsindia.llm.framework.model;

import java.util.*;

public class Message {
    public enum Role { system, user, assistant, tool }

    private Role role;
    private String content;
    private String name;
    private List<ToolCall> toolCalls;
    private List<ContentPart> contentParts;

    public static class ContentPart {
        private String type;
        private String text;
        private String imageUrl;

        public static ContentPart text(String text) {
            ContentPart p = new ContentPart();
            p.type = "text"; p.text = text; return p;
        }

        public static ContentPart image(String url) {
            ContentPart p = new ContentPart();
            p.type = "image_url"; p.imageUrl = url; return p;
        }

        public String getType() { return type; }
        public String getText() { return text; }
        public String getImageUrl() { return imageUrl; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("type", type);
            if ("text".equals(type)) m.put("text", text);
            else if ("image_url".equals(type))
                m.put("image_url", Map.of("url", imageUrl));
            return m;
        }
    }

    public Message() {}
    public Message(Role role, String content) { this.role = role; this.content = content; }

    public static Message ofSystem(String content) { return new Message(Role.system, content); }
    public static Message ofUser(String content) { return new Message(Role.user, content); }
    public static Message ofAssistant(String content) { return new Message(Role.assistant, content); }
    public static Message ofTool(String content) { return new Message(Role.tool, content); }

    public static MessageBuilder user() { return new MessageBuilder(Role.user); }
    public static MessageBuilder assistant() { return new MessageBuilder(Role.assistant); }

    public static class MessageBuilder {
        private final Message msg = new Message();
        private MessageBuilder(Role role) { msg.role = role; }
        public MessageBuilder text(String content) { msg.content = content; return this; }
        public MessageBuilder image(String url) {
            if (msg.contentParts == null) msg.contentParts = new ArrayList<>();
            msg.contentParts.add(ContentPart.image(url)); return this;
        }
        public MessageBuilder name(String name) { msg.name = name; return this; }
        public Message build() { return msg; }
    }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> tc) { this.toolCalls = tc; }
    public List<ContentPart> getContentParts() { return contentParts; }
    public void setContentParts(List<ContentPart> p) { this.contentParts = p; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role.name());
        if (contentParts != null && !contentParts.isEmpty()) {
            List<Map<String, Object>> parts = new ArrayList<>();
            contentParts.forEach(p -> parts.add(p.toMap()));
            m.put("content", parts);
        } else {
            m.put("content", content);
        }
        if (name != null) m.put("name", name);
        if (toolCalls != null) {
            List<Map<String, Object>> calls = new ArrayList<>();
            toolCalls.forEach(tc -> calls.add(tc.toMap()));
            m.put("tool_calls", calls);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Message fromMap(Map<String, Object> m) {
        Message msg = new Message();
        msg.setRole(Role.valueOf((String) m.get("role")));
        msg.setContent((String) m.get("content"));
        msg.setName((String) m.get("name"));
        Object content = m.get("content");
        if (content instanceof List) {
            msg.setContentParts(new ArrayList<>());
            for (Object p : (List<?>) content) {
                if (p instanceof Map) {
                    Map<String, Object> pm = (Map<String, Object>) p;
                    ContentPart part = new ContentPart();
                    part.type = (String) pm.get("type");
                    Object txt = pm.get("text");
                    if (txt != null) part.text = txt.toString();
                    msg.getContentParts().add(part);
                }
            }
        }
        Object calls = m.get("tool_calls");
        if (calls instanceof List) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (Object c : (List<?>) calls) toolCalls.add(ToolCall.fromMap((Map<String, Object>) c));
            msg.setToolCalls(toolCalls);
        }
        return msg;
    }
}