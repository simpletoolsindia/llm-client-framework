package in.simpletools.llm.framework.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable provider-neutral chat request.
 *
 * <p>Adapters turn this record into provider-specific JSON. Application code
 * normally uses {@link in.simpletools.llm.framework.client.LLMClient#chat(String)},
 * but advanced integrations can build requests directly.</p>
 *
 * @param model model name to call
 * @param messages ordered conversation messages
 * @param temperature sampling temperature, when supported
 * @param maxTokens maximum output tokens, when supported
 * @param topP nucleus sampling value, when supported
 * @param frequencyPenalty frequency penalty, when supported
 * @param presencePenalty presence penalty, when supported
 * @param stop stop sequences
 * @param stream whether to request streaming output
 * @param tools tool schemas available to the model
 * @param toolChoice optional provider tool-choice directive
 * @param seed optional deterministic seed, when supported
 */
public record LLMRequest(
    String model,
    List<Message> messages,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Double frequencyPenalty,
    Double presencePenalty,
    String[] stop,
    boolean stream,
    List<Tool> tools,
    String toolChoice,
    Double seed
) {
    public LLMRequest {
        if (messages == null) messages = List.of();
        if (tools == null) tools = List.of();
        if (stop == null) stop = new String[0];
    }

    /** @return OpenAI-style request map used by compatible adapters */
    public Map<String, Object> toMap() {
        var m = new HashMap<String, Object>();
        m.put("model", model);
        m.put("stream", stream);
        m.put("messages", messages.stream().map(Message::toMap).collect(Collectors.toList()));
        if (temperature != null) m.put("temperature", temperature);
        if (maxTokens != null) m.put("max_tokens", maxTokens);
        if (topP != null) m.put("top_p", topP);
        if (frequencyPenalty != null) m.put("frequency_penalty", frequencyPenalty);
        if (presencePenalty != null) m.put("presence_penalty", presencePenalty);
        if (stop.length > 0) m.put("stop", stop);
        if (!tools.isEmpty()) m.put("tools", tools.stream().map(Tool::toMap).collect(Collectors.toList()));
        if (toolChoice != null) m.put("tool_choice", toolChoice);
        if (seed != null) m.put("seed", seed);
        return m;
    }

    /** @return fluent request builder */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link LLMRequest}.
     */
    public static class Builder {
        private String model;
        private List<Message> messages = new ArrayList<>();
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private String[] stop;
        private boolean stream = true;
        private List<Tool> tools;
        private String toolChoice;
        private Double seed;

        /** @param m model name @return this builder */
        public Builder model(String m) { model = m; return this; }
        /** @param msgs full ordered message list @return this builder */
        public Builder messages(List<Message> msgs) { messages = new ArrayList<>(msgs); return this; }
        /** @param msg message to append @return this builder */
        public Builder addMessage(Message msg) { messages.add(msg); return this; }
        /** @param content system prompt to append @return this builder */
        public Builder system(String content) { return addMessage(Message.ofSystem(content)); }
        /** @param content user message to append @return this builder */
        public Builder user(String content) { return addMessage(Message.ofUser(content)); }
        /** @param t sampling temperature @return this builder */
        public Builder temperature(Double t) { temperature = t; return this; }
        /** @param m maximum output tokens @return this builder */
        public Builder maxTokens(Integer m) { maxTokens = m; return this; }
        /** @param p top-p sampling value @return this builder */
        public Builder topP(Double p) { topP = p; return this; }
        /** @param p frequency penalty @return this builder */
        public Builder frequencyPenalty(Double p) { frequencyPenalty = p; return this; }
        /** @param p presence penalty @return this builder */
        public Builder presencePenalty(Double p) { presencePenalty = p; return this; }
        /** @param s stream flag @return this builder */
        public Builder stream(boolean s) { stream = s; return this; }
        /** @param t tool schemas @return this builder */
        public Builder tools(List<Tool> t) { tools = t != null ? new ArrayList<>(t) : null; return this; }
        /** @param tc provider tool choice directive @return this builder */
        public Builder toolChoice(String tc) { toolChoice = tc; return this; }
        /** @param s stop sequences @return this builder */
        public Builder stop(String... s) { stop = s; return this; }
        /** @param s deterministic seed, when supported @return this builder */
        public Builder seed(Double s) { seed = s; return this; }
        /** @return immutable request */
        public LLMRequest build() {
            var msgs = messages != null ? new ArrayList<>(messages) : new ArrayList<Message>();
            return new LLMRequest(model, List.copyOf(msgs), temperature, maxTokens, topP,
                frequencyPenalty, presencePenalty, stop, stream,
                tools != null ? List.copyOf(tools) : List.of(), toolChoice, seed);
        }
    }
}
