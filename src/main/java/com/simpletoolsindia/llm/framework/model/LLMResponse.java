package com.simpletoolsindia.llm.framework.model;

import java.util.*;

public class LLMResponse {
    private String model;
    private Message message;
    private String finishReason;
    private Long totalDuration;
    private Usage usage;
    private boolean done;
    private String id;
    private Long created;
    private String object;

    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private int inputTokens;
        private int outputTokens;

        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int p) { this.promptTokens = p; }
        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int c) { this.completionTokens = c; }
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int t) { this.totalTokens = t; }
        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int i) { this.inputTokens = i; }
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int o) { this.outputTokens = o; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("prompt_tokens", promptTokens);
            m.put("completion_tokens", completionTokens);
            m.put("total_tokens", totalTokens);
            return m;
        }
    }

    // Getters & Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    public Long getTotalDuration() { return totalDuration; }
    public void setTotalDuration(Long totalDuration) { this.totalDuration = totalDuration; }
    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public String getContent() {
        return message != null ? message.getContent() : null;
    }

    public String getContentOrEmpty() {
        return message != null && message.getContent() != null
            ? message.getContent() : "";
    }

    public boolean hasToolCalls() {
        return message != null && message.getToolCalls() != null
            && !message.getToolCalls().isEmpty();
    }

    public List<ToolCall> getToolCalls() {
        return hasToolCalls() ? message.getToolCalls() : null;
    }

    public static LLMResponse fromMap(Map<String, Object> m) {
        LLMResponse resp = new LLMResponse();
        resp.setModel((String) m.get("model"));
        resp.setFinishReason((String) m.get("finish_reason"));
        resp.setDone(Boolean.TRUE.equals(m.get("done")));

        if (m.get("total_duration") != null)
            resp.setTotalDuration(((Number) m.get("total_duration")).longValue());

        Object usage = m.get("usage");
        if (usage instanceof Map) {
            Usage u = new Usage();
            Map<String, Object> um = (Map<String, Object>) usage;
            u.setPromptTokens(intVal(um.get("prompt_tokens")));
            u.setCompletionTokens(intVal(um.get("completion_tokens")));
            u.setTotalTokens(intVal(um.get("total_tokens")));
            resp.setUsage(u);
        }

        Object msg = m.get("message");
        if (msg instanceof Map) {
            resp.setMessage(Message.fromMap((Map<String, Object>) msg));
        }

        return resp;
    }

    private static int intVal(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }
}