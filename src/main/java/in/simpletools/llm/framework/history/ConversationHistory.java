package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import java.util.*;

/**
 * In-memory conversation history with automatic size limiting.
 * The simplest way to maintain chat context within a session.
 *
 * <pre>
 * {@code
 * ConversationHistory history = new ConversationHistory();
 * history.addUser("Hello");
 * history.addAssistant("Hi there!");
 * history.addUser("Tell me about dogs");
 * // ...
 * List<Message> msgs = history.getMessages();
 * history.clear(); // reset
 * }
 * </pre>
 *
 * <p>By default, keeps the last 100 messages. Older messages are
 * trimmed from the beginning to stay within the limit.
 *
 * @see RedisHistory for persistent, cross-session history
 */
public class ConversationHistory {
    private final List<Message> messages = new ArrayList<>();
    private final int maxHistory;

    /**
     * Creates history with default max of 100 messages.
     */
    public ConversationHistory() { this(100); }

    /**
     * Creates history with custom message limit.
     * @param maxHistory maximum messages to retain
     */
    public ConversationHistory(int maxHistory) { this.maxHistory = maxHistory; }

    /** Add a user message to history. */
    public void addUser(String content) { add(Message.ofUser(content)); }

    /** Add an assistant (LLM) response to history. */
    public void addAssistant(String content) { add(Message.ofAssistant(content)); }

    /** Add a system message to history. */
    public void addSystem(String content) { add(Message.ofSystem(content)); }

    /** Add a tool execution result to history. */
    public void addTool(String content) { add(Message.ofTool(content)); }

    /**
     * Add any message type. Trims history if over limit.
     * @param message the message to add
     */
    public void add(Message message) { messages.add(message); trim(); }

    /** Get a copy of all messages. */
    public List<Message> getMessages() { return new ArrayList<>(messages); }

    /**
     * Get the last N messages.
     * @param count number of messages to retrieve
     */
    public List<Message> getLast(int count) {
        int size = messages.size();
        return messages.subList(Math.max(0, size - count), size);
    }

    /** Clear all messages from memory. */
    public void clear() { messages.clear(); }

    /**
     * Remove the last N messages from history.
     * Useful for backing out a failed response.
     */
    public void clearLastN(int n) {
        while (messages.size() > 0 && n-- > 0) messages.remove(messages.size() - 1);
    }

    /** Number of messages currently in history. */
    public int size() { return messages.size(); }

    /** Conversation ID placeholder for ConversationHistoryStore interface. */
    public String getConversationId() { return "in-memory"; }

    /** Trim oldest messages if over limit. */
    private void trim() { while (messages.size() > maxHistory) messages.remove(0); }
}