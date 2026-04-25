package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory conversation history with automatic size limiting.
 */
public class ConversationHistory {
    private final List<Message> messages = new ArrayList<>();
    private final int maxHistory;

    public ConversationHistory() { this(100); }
    public ConversationHistory(int maxHistory) { this.maxHistory = maxHistory; }

    public void addUser(String content) { add(Message.ofUser(content)); }
    public void addAssistant(String content) { add(Message.ofAssistant(content)); }
    public void addSystem(String content) { add(Message.ofSystem(content)); }
    public void addTool(String content) { add(Message.ofTool(content)); }

    public void add(Message message) { messages.add(message); trim(); }

    public List<Message> getMessages() { return new ArrayList<>(messages); }

    public void replaceAll(List<Message> newMessages) {
        messages.clear();
        if (newMessages != null) messages.addAll(newMessages);
        trim();
    }

    public List<Message> getLast(int count) {
        int size = messages.size();
        return messages.subList(Math.max(0, size - count), size);
    }

    public void clear() { messages.clear(); }

    public void clearLastN(int n) {
        int end = Math.max(0, messages.size() - n);
        while (messages.size() > end) messages.remove(messages.size() - 1);
    }

    public int size() { return messages.size(); }
    public String getConversationId() { return "in-memory"; }

    private void trim() { while (messages.size() > maxHistory) messages.remove(0); }
}
