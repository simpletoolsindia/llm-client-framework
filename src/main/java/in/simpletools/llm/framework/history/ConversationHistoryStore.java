package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import java.util.*;

/**
 * Interface for conversation history stores.
 * Implement this to add custom storage backends (Redis, DB, file, etc).
 */
public interface ConversationHistoryStore {
    void addUser(String content);
    void addAssistant(String content);
    void addSystem(String content);
    void addTool(String content);
    void add(Message message);
    List<Message> getMessages();
    List<Message> getLast(int count);
    void clear();
    int size();
    String getConversationId();
}
