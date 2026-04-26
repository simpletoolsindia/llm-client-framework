package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory conversation history with automatic size limiting.
 *
 * <p>The client stores every user, assistant, system, and tool message here.
 * When the number of messages exceeds {@code maxHistory}, the oldest messages
 * are dropped. This class is intentionally simple and process-local; use
 * {@link ConversationHistoryStore} or {@link RedisHistory} for persistence.</p>
 */
public class ConversationHistory {
    private final List<Message> messages = new ArrayList<>();
    private final int maxHistory;

    /** Create an in-memory history retaining up to 100 messages. */
    public ConversationHistory() { this(100); }
    /**
     * Create an in-memory history with a custom message cap.
     *
     * @param maxHistory maximum messages to keep
     */
    public ConversationHistory(int maxHistory) { this.maxHistory = maxHistory; }

    /** @param content user message content */
    public void addUser(String content) { add(Message.ofUser(content)); }
    /** @param content assistant message content */
    public void addAssistant(String content) { add(Message.ofAssistant(content)); }
    /** @param content system message content */
    public void addSystem(String content) { add(Message.ofSystem(content)); }
    /** @param content tool result content */
    public void addTool(String content) { add(Message.ofTool(content)); }

    /** @param message message to append */
    public void add(Message message) { messages.add(message); trim(); }

    /** @return defensive copy of all retained messages */
    public List<Message> getMessages() { return new ArrayList<>(messages); }

    /**
     * Replace all retained messages.
     *
     * @param newMessages replacement message list; null clears history
     */
    public void replaceAll(List<Message> newMessages) {
        messages.clear();
        if (newMessages != null) messages.addAll(newMessages);
        trim();
    }

    /**
     * Return the last {@code count} messages.
     *
     * @param count number of recent messages requested
     * @return view of recent messages
     */
    public List<Message> getLast(int count) {
        int size = messages.size();
        return messages.subList(Math.max(0, size - count), size);
    }

    /** Remove all messages. */
    public void clear() { messages.clear(); }

    /**
     * Remove the most recent {@code n} messages.
     *
     * @param n number of messages to remove
     */
    public void clearLastN(int n) {
        int end = Math.max(0, messages.size() - n);
        while (messages.size() > end) messages.remove(messages.size() - 1);
    }

    /** @return number of retained messages */
    public int size() { return messages.size(); }
    /** @return stable identifier for this in-memory conversation */
    public String getConversationId() { return "in-memory"; }

    private void trim() { while (messages.size() > maxHistory) messages.remove(0); }
}
