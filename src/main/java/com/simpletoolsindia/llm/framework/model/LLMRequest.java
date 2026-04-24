package com.simpletoolsindia.llm.framework.model;

import java.util.*;

public class LLMRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private String[] stop;
    private boolean stream = true;
    private List<Tool> tools;
    private String toolChoice; // "auto", "none", "required"
    private Double seed;
    private String responseFormat;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }
    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }
    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }
    public String[] getStop() { return stop; }
    public void setStop(String[] stop) { this.stop = stop; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    public List<Tool> getTools() { return tools; }
    public void setTools(List<Tool> tools) { this.tools = tools; }
    public String getToolChoice() { return toolChoice; }
    public void setToolChoice(String toolChoice) { this.toolChoice = toolChoice; }
    public Double getSeed() { return seed; }
    public void setSeed(Double seed) { this.seed = seed; }
    public String getResponseFormat() { return responseFormat; }
    public void setResponseFormat(String responseFormat) { this.responseFormat = responseFormat; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("model", model);
        m.put("stream", stream);

        List<Map<String, Object>> msgList = new ArrayList<>();
        if (messages != null) messages.forEach(msg -> msgList.add(msg.toMap()));
        m.put("messages", msgList);

        if (temperature != null) m.put("temperature", temperature);
        if (maxTokens != null) m.put("max_tokens", maxTokens);
        if (topP != null) m.put("top_p", topP);
        if (frequencyPenalty != null) m.put("frequency_penalty", frequencyPenalty);
        if (presencePenalty != null) m.put("presence_penalty", presencePenalty);
        if (stop != null) m.put("stop", stop);
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> tlist = new ArrayList<>();
            tools.forEach(t -> tlist.add(t.toMap()));
            m.put("tools", tlist);
        }
        if (toolChoice != null) m.put("tool_choice", toolChoice);
        if (seed != null) m.put("seed", seed);
        if (responseFormat != null) m.put("response_format", Map.of("type", responseFormat));

        return m;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final LLMRequest req = new LLMRequest();

        public Builder model(String m) { req.model = m; return this; }
        public Builder messages(List<Message> msgs) { req.messages = msgs; return this; }
        public Builder addMessage(Message msg) {
            if (req.messages == null) req.messages = new ArrayList<>();
            req.messages.add(msg); return this;
        }
        public Builder system(String content) {
            return addMessage(Message.ofSystem(content));
        }
        public Builder user(String content) {
            return addMessage(Message.ofUser(content));
        }
        public Builder temperature(Double t) { req.temperature = t; return this; }
        public Builder maxTokens(Integer m) { req.maxTokens = m; return this; }
        public Builder topP(Double p) { req.topP = p; return this; }
        public Builder stream(boolean s) { req.stream = s; return this; }
        public Builder tools(List<Tool> tools) { req.tools = tools; return this; }
        public Builder stop(String... s) { req.stop = s; return this; }
        public LLMRequest build() { return req; }
    }
}