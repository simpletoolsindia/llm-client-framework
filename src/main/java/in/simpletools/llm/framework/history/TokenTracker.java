package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.*;
import java.util.*;

/**
 * Tracks token usage and context window for the current conversation.
 * Provides methods to estimate tokens and calculate remaining context.
 *
 * <pre>
 * {@code
 * TokenTracker tracker = new TokenTracker();
 * tracker.setModelContextLimit("gpt-4o", 128000);
 *
 * LLMClient client = LLMClientFactory.openAI("gpt-4o", "sk-...");
 * client.setTokenTracker(tracker);
 *
 * // After each chat
 * ContextInfo info = tracker.getContextInfo();
 * System.out.println("Used: " + info.usedTokens + " / " + info.totalLimit);
 * System.out.println("Remaining: " + info.remainingTokens);
 * System.out.println("Usage: " + info.usagePercent + "%");
 * }
 * </pre>
 */
public class TokenTracker {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String model;
    private long modelLimit;
    private int messageCount;
    private final List<TokenSnapshot> history;

    /** Snapshot of token counts after each message. */
    public record TokenSnapshot(int promptTokens, int completionTokens, int totalTokens, String role) {}

    /** Context window information. */
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
        public String summary() {
            return String.format("[%s] %d / %d tokens (%.1f%%) | prompt=%d, completion=%d | %d messages",
                model, usedTokens, totalLimit, usagePercent, promptTokens, completionTokens, messageCount);
        }
    }

    public TokenTracker() {
        this.history = new ArrayList<>();
    }

    public TokenTracker(String model, long modelLimit) {
        this();
        this.model = model;
        this.modelLimit = modelLimit;
    }

    // ===== Configuration =====

    /** Set the model and its context window limit. */
    public void setModel(String model, long limit) {
        this.model = model;
        this.modelLimit = limit;
    }

    /** Configure known model limits. */
    public void setModelContextLimit(String model, long limit) {
        this.model = model;
        this.modelLimit = limit;
    }

    // ===== Token Estimation =====

    /**
     * Update from LLM response usage data (most accurate when available).
     */
    public void updateFromUsage(LLMResponse response) {
        if (response == null || response.getUsage() == null) return;

        LLMResponse.Usage u = response.getUsage();
        int p = u.getPromptTokens() > 0 ? u.getPromptTokens() : u.getInputTokens();
        int c = u.getCompletionTokens() > 0 ? u.getCompletionTokens() : u.getOutputTokens();

        this.promptTokens = p;
        this.completionTokens = c;
        this.totalTokens = u.getTotalTokens() > 0 ? u.getTotalTokens() : (p + c);
        this.model = response.getModel() != null ? response.getModel() : this.model;
    }

    public void updateFromUsage(LLMResponse response, int currentMessageCount) {
        updateFromUsage(response);
        this.messageCount = currentMessageCount;
    }

    /**
     * Estimate tokens from a list of messages (approximate when usage data unavailable).
     * Uses a simple character-based approximation: ~4 chars per token.
     */
    public int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            String content = msg.getContent();
            if (content != null) {
                total += estimateTextTokens(content);
            }
            if (msg.getContentParts() != null) {
                for (Message.ContentPart part : msg.getContentParts()) {
                    if (part.getText() != null) total += estimateTextTokens(part.getText());
                    if (part.getImageUrl() != null) total += 256;
                }
            }
            if (msg.getToolCalls() != null) {
                for (ToolCall call : msg.getToolCalls()) {
                    if (call.getFunction() != null) {
                        total += estimateTextTokens(call.getFunction().getName());
                        total += estimateTextTokens(String.valueOf(call.getFunction().getArguments()));
                    }
                }
            }
            // Role overhead
            total += 4;
        }
        // Conversation format overhead
        total += 3;
        return total;
    }

    /** Estimate tokens for a single text string. */
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Simple approximation: 4 characters per token on average
        return (text.length() / 4) + 1;
    }

    /**
     * Update from message list. Use this when the provider doesn't return usage data.
     */
    public void updateFromMessages(List<Message> messages, int completionTokens) {
        this.promptTokens = estimateTokens(messages);
        this.completionTokens = completionTokens;
        this.totalTokens = this.promptTokens + this.completionTokens;
        this.messageCount = messages != null ? messages.size() : 0;
        recordSnapshot("update");
    }

    /** Re-estimate token usage from the current conversation history. */
    public void syncWithConversation(List<Message> messages) {
        this.promptTokens = estimateTokens(messages);
        this.completionTokens = 0;
        this.totalTokens = this.promptTokens;
        this.messageCount = messages != null ? messages.size() : 0;
        recordSnapshot("sync");
    }

    // ===== Context Info =====

    /** Get current context window usage. */
    public ContextInfo getContextInfo() {
        long limit = resolveModelLimit();
        int used = totalTokens > 0 ? totalTokens : (promptTokens + completionTokens);
        int remaining = (int) Math.max(0, limit - used);
        double percent = limit > 0 ? (used * 100.0 / limit) : 0;
        return new ContextInfo(model, limit, used, remaining, promptTokens, completionTokens, percent, messageCount);
    }

    /** Check if we're running low on context. */
    public boolean isNearLimit() {
        ContextInfo info = getContextInfo();
        return info.remainingTokens() < (info.totalLimit() * 0.1); // < 10% remaining
    }

    /** Check if usage has crossed a specific percentage threshold. */
    public boolean isNearLimit(double usageThresholdPercent) {
        return getContextInfo().usagePercent() >= usageThresholdPercent;
    }

    /** Check if we're out of context. */
    public boolean isOverLimit() {
        ContextInfo info = getContextInfo();
        return info.remainingTokens() <= 0;
    }

    /** Get remaining tokens in context. */
    public int getRemainingTokens() {
        return getContextInfo().remainingTokens();
    }

    // ===== Message-level Tracking =====

    /** Record a user message. */
    public void recordUser(String content) {
        promptTokens += estimateTextTokens(content) + 4;
        totalTokens = promptTokens + completionTokens;
        messageCount++;
        recordSnapshot("user");
    }

    /** Record an assistant message. */
    public void recordAssistant(String content) {
        completionTokens += estimateTextTokens(content) + 4;
        totalTokens = promptTokens + completionTokens;
        messageCount++;
        recordSnapshot("assistant");
    }

    /** Record a tool result. */
    public void recordTool(String content) {
        promptTokens += estimateTextTokens(content) + 1;
        totalTokens = promptTokens + completionTokens;
        messageCount++;
        recordSnapshot("tool");
    }

    /** Record actual completion tokens from a response. */
    public void recordCompletionTokens(int tokens) {
        completionTokens = tokens;
        totalTokens = promptTokens + completionTokens;
    }

    private void recordSnapshot(String role) {
        history.add(new TokenSnapshot(promptTokens, completionTokens, totalTokens, role));
        // Keep last 100 snapshots
        while (history.size() > 100) history.remove(0);
    }

    // ===== History =====

    public List<TokenSnapshot> getHistory() { return new ArrayList<>(history); }

    public void reset() {
        promptTokens = 0;
        completionTokens = 0;
        totalTokens = 0;
        messageCount = 0;
        history.clear();
    }

    // ===== Getters =====

    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public String getModel() { return model; }
    public long getModelLimit() { return modelLimit; }

    // ===== Known Model Context Limits =====

    private long resolveModelLimit() {
        if (modelLimit > 0) return modelLimit;
        return detectLimitForModel(model);
    }

    /** Common model context limits (tokens). */
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

    /** Look up context limit for a known model. */
    public static long getLimitForModel(String model) {
        return detectLimitForModel(model);
    }

    /** Best-effort automatic context window detection based on common model names. */
    public static long detectLimitForModel(String model) {
        if (model == null || model.isBlank()) return 4096L;

        String normalized = model.toLowerCase(Locale.ROOT).trim();
        int tagSeparator = normalized.indexOf(':');
        if (tagSeparator > 0) normalized = normalized.substring(0, tagSeparator);

        Long exact = KNOWN_LIMITS.get(normalized);
        if (exact != null) return exact;

        for (Map.Entry<String, Long> entry : KNOWN_LIMITS.entrySet()) {
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

    /** Print context info in human-readable format. */
    public static String formatContextInfo(ContextInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Token Usage ===\n");
        sb.append("Model: ").append(info.model()).append("\n");
        sb.append("Context Limit: ").append(info.totalLimit()).append(" tokens\n");
        sb.append("Prompt: ").append(info.promptTokens()).append(" tokens\n");
        sb.append("Completion: ").append(info.completionTokens()).append(" tokens\n");
        sb.append("Total Used: ").append(info.usedTokens()).append(" tokens\n");
        sb.append("Remaining: ").append(info.remainingTokens()).append(" tokens\n");
        sb.append("Usage: ").append(String.format("%.1f", info.usagePercent())).append("%\n");
        if (info.usagePercent() > 80) sb.append("WARNING: Context window >80% full\n");
        if (info.usagePercent() > 95) sb.append("WARNING: Context window >95% full - consider clearing history\n");
        return sb.toString();
    }
}
