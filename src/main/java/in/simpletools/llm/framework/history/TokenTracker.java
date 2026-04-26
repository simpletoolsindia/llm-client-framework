package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks token usage and context-window pressure for a conversation.
 *
 * <p>The tracker combines exact provider usage when available with lightweight
 * local estimates when usage is missing. The client uses this information to
 * decide when conversation history should be compacted.</p>
 */
public class TokenTracker {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String model;
    private long modelLimit;
    private int messageCount;
    private final List<TokenSnapshot> history;

    /**
     * Point-in-time token counters recorded after updates.
     *
     * @param promptTokens prompt/input token count
     * @param completionTokens completion/output token count
     * @param totalTokens total token count
     * @param role update source label
     */
    public record TokenSnapshot(int promptTokens, int completionTokens, int totalTokens, String role) {}

    /**
     * Current context-window usage summary.
     *
     * @param model model name
     * @param totalLimit known or estimated context window
     * @param usedTokens current used token count
     * @param remainingTokens estimated remaining tokens
     * @param promptTokens prompt/input tokens
     * @param completionTokens completion/output tokens
     * @param usagePercent percentage of context window used
     * @param messageCount number of messages represented
     */
    public record ContextInfo(
        String model,
        long totalLimit,
        int usedTokens,
        int remainingTokens,
        int promptTokens,
        int completionTokens,
        double usagePercent,
        int messageCount
    ) {
        /** @return compact human-readable summary */
        public String summary() {
            return String.format("[%s] %d / %d tokens (%.1f%%) | prompt=%d, completion=%d | %d messages",
                model, usedTokens, totalLimit, usagePercent, promptTokens, completionTokens, messageCount);
        }
    }

    /** Create an empty token tracker with model limit auto-detection. */
    public TokenTracker() { this.history = new ArrayList<>(); }

    /**
     * Create a tracker for a known model/context window.
     *
     * @param model model name
     * @param modelLimit context window token limit
     */
    public TokenTracker(String model, long modelLimit) {
        this();
        this.model = model;
        this.modelLimit = modelLimit;
    }

    /** @param model model name @param limit context window token limit */
    public void setModel(String model, long limit) {
        this.model = model;
        this.modelLimit = limit;
    }

    public void setModelContextLimit(String model, long limit) { setModel(model, limit); }

    // ===== Token Estimation =====

    /** @param response provider response containing usage data */
    public void updateFromUsage(LLMResponse response) { updateFromUsage(response, messageCount); }

    /** @param response provider response @param currentMessageCount current history size */
    public void updateFromUsage(LLMResponse response, int currentMessageCount) {
        if (response == null || response.usage() == null) {
            this.messageCount = currentMessageCount;
            return;
        }

        var u = response.usage();
        int p = u.promptTokens() > 0 ? u.promptTokens() : u.inputTokens();
        int c = u.completionTokens() > 0 ? u.completionTokens() : u.outputTokens();

        this.promptTokens = p;
        this.completionTokens = c;
        this.totalTokens = u.totalTokens() > 0 ? u.totalTokens() : (p + c);
        this.model = response.model() != null ? response.model() : this.model;
        this.messageCount = currentMessageCount;
    }

    /**
     * Estimate tokens from a list of messages.
     * Uses ~4 chars per token approximation.
     */
    public int estimateTokens(List<Message> messages) {
        return messages.stream()
            .mapToInt(this::estimateMessageTokens)
            .sum() + 3; // conversation format overhead
    }

    private int estimateMessageTokens(Message msg) {
        int total = 0;
        if (msg.content() != null) total += estimateTextTokens(msg.content());
        for (var part : msg.contentParts()) {
            if (part.text() != null) total += estimateTextTokens(part.text());
            if (part.imageUrl() != null) total += 256; // image token estimate
        }
        for (var call : msg.toolCalls()) {
            if (call.function() != null) {
                total += estimateTextTokens(call.function().name());
                total += estimateTextTokens(String.valueOf(call.function().arguments()));
            }
        }
        return total + 4; // role overhead
    }

    /** @param text text to estimate @return approximate token count */
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (text.length() / 4) + 1;
    }

    public void updateFromMessages(List<Message> messages, int completionTokens) {
        this.promptTokens = estimateTokens(messages);
        this.completionTokens = completionTokens;
        this.totalTokens = this.promptTokens + this.completionTokens;
        this.messageCount = messages != null ? messages.size() : 0;
        recordSnapshot("update");
    }

    /** @param messages current conversation messages to estimate from */
    public void syncWithConversation(List<Message> messages) {
        this.promptTokens = estimateTokens(messages);
        this.completionTokens = 0;
        this.totalTokens = this.promptTokens;
        this.messageCount = messages != null ? messages.size() : 0;
        recordSnapshot("sync");
    }

    /** @return current context usage summary */
    public ContextInfo getContextInfo() {
        long limit = resolveModelLimit();
        int used = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
        int remaining = (int) Math.max(0, limit - used);
        double percent = limit > 0 ? (used * 100.0 / limit) : 0;
        return new ContextInfo(model, limit, used, remaining, promptTokens, completionTokens, percent, messageCount);
    }

    public boolean isNearLimit() { return isNearLimit(90.0); }
    public boolean isNearLimit(double usageThresholdPercent) { return getContextInfo().usagePercent() >= usageThresholdPercent; }
    public boolean isOverLimit() { return getContextInfo().remainingTokens() <= 0; }
    public int getRemainingTokens() { return getContextInfo().remainingTokens(); }

    // ===== Message-level Tracking =====

    public void recordUser(String content) {
        promptTokens += estimateTextTokens(content) + 4;
        totalTokens = promptTokens + completionTokens;
        messageCount++;
        recordSnapshot("user");
    }

    public void recordAssistant(String content) {
        completionTokens += estimateTextTokens(content) + 4;
        totalTokens = promptTokens + completionTokens;
        messageCount++;
        recordSnapshot("assistant");
    }

    public void recordTool(String content) {
        promptTokens += estimateTextTokens(content) + 1;
        totalTokens = promptTokens + completionTokens;
        messageCount++;
        recordSnapshot("tool");
    }

    public void recordCompletionTokens(int tokens) {
        completionTokens = tokens;
        totalTokens = promptTokens + completionTokens;
    }

    private void recordSnapshot(String role) {
        history.add(new TokenSnapshot(promptTokens, completionTokens, totalTokens, role));
        while (history.size() > 100) history.remove(0);
    }

    public List<TokenSnapshot> getHistory() { return new ArrayList<>(history); }

    public void reset() {
        promptTokens = 0;
        completionTokens = 0;
        totalTokens = 0;
        messageCount = 0;
        history.clear();
    }

    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public String getModel() { return model; }
    public long getModelLimit() { return modelLimit; }

    private long resolveModelLimit() {
        if (modelLimit > 0) return modelLimit;
        return detectLimitForModel(model);
    }

    // ===== Known Model Context Limits =====

    public static final Map<String, Long> KNOWN_LIMITS = Map.ofEntries(
        Map.entry("gpt-4o", 128000L),
        Map.entry("gpt-4o-mini", 128000L),
        Map.entry("gpt-4-turbo", 128000L),
        Map.entry("gpt-4.1", 1047576L),
        Map.entry("gpt-4.1-mini", 1047576L),
        Map.entry("gpt-4.1-nano", 1047576L),
        Map.entry("gpt-4", 8192L),
        Map.entry("gpt-3.5-turbo", 16385L),
        Map.entry("claude-3-5-sonnet", 200000L),
        Map.entry("claude-3-5-sonnet-20241022", 200000L),
        Map.entry("claude-3-7-sonnet", 200000L),
        Map.entry("claude-sonnet-4", 200000L),
        Map.entry("claude-opus-4", 200000L),
        Map.entry("claude-3-opus", 200000L),
        Map.entry("claude-3-haiku", 200000L),
        Map.entry("claude-3-sonnet", 200000L),
        Map.entry("deepseek-chat", 64000L),
        Map.entry("deepseek-coder", 128000L),
        Map.entry("deepseek-reasoner", 64000L),
        Map.entry("llama-3.1-70b", 128000L),
        Map.entry("llama-3.1-8b", 128000L),
        Map.entry("llama-3.2", 128000L),
        Map.entry("llama3.1", 128000L),
        Map.entry("llama3.2", 128000L),
        Map.entry("gemma4", 32000L),
        Map.entry("gemma3", 32000L),
        Map.entry("mistral-large", 128000L),
        Map.entry("mistral-small", 32000L),
        Map.entry("codestral", 32000L)
    );

    public static long getLimitForModel(String model) { return detectLimitForModel(model); }

    public static long detectLimitForModel(String model) {
        if (model == null || model.isBlank()) return 4096L;

        String normalized = model.toLowerCase(Locale.ROOT).trim();
        int tagSeparator = normalized.indexOf(':');
        if (tagSeparator > 0) normalized = normalized.substring(0, tagSeparator);

        Long exact = KNOWN_LIMITS.get(normalized);
        if (exact != null) return exact;

        for (var entry : KNOWN_LIMITS.entrySet()) {
            if (normalized.startsWith(entry.getKey()) || normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        if (normalized.contains("claude")) return 200000L;
        if (normalized.contains("gpt-4o")) return 128000L;
        if (normalized.contains("gpt-4.1")) return 1047576L;
        if (normalized.contains("gpt-4")) return 128000L;
        if (normalized.contains("gpt-3.5")) return 16385L;
        if (normalized.contains("llama") || normalized.contains("deepseek") || normalized.contains("mistral")) return 128000L;
        if (normalized.contains("gemma")) return 32000L;

        return 4096L;
    }

    public static String formatContextInfo(ContextInfo info) {
        return new StringBuilder()
            .append("=== Token Usage ===\n")
            .append("Model: ").append(info.model()).append("\n")
            .append("Context Limit: ").append(info.totalLimit()).append(" tokens\n")
            .append("Prompt: ").append(info.promptTokens()).append(" tokens\n")
            .append("Completion: ").append(info.completionTokens()).append(" tokens\n")
            .append("Total Used: ").append(info.usedTokens()).append(" tokens\n")
            .append("Remaining: ").append(info.remainingTokens()).append(" tokens\n")
            .append("Usage: ").append(String.format("%.1f", info.usagePercent())).append("%\n")
            .append(info.usagePercent() > 80 ? "WARNING: Context window >80% full\n" : "")
            .append(info.usagePercent() > 95 ? "WARNING: Context window >95% full - consider clearing history\n" : "")
            .toString();
    }
}
