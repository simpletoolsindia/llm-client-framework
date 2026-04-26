package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import java.util.*;

/**
 * Pluggable conversation history store contract.
 *
 * <p>Implement this interface to persist chat history in Redis, a database,
 * files, or another application-specific backend. Implementations should retain
 * message order exactly as appended because model behavior depends on sequence.</p>
 */
public interface ConversationHistoryStore {
    /** @param content user message content */
    void addUser(String content);
    /** @param content assistant message content */
    void addAssistant(String content);
    /** @param content system message content */
    void addSystem(String content);
    /** @param content tool result content */
    void addTool(String content);
    /** @param message message to append */
    void add(Message message);
    /** @return all retained messages in order */
    List<Message> getMessages();
    /** @param count number of recent messages requested @return latest messages in order */
    List<Message> getLast(int count);
    /** Remove all retained messages. */
    void clear();
    /** @return number of retained messages */
    int size();
    /** @return application-defined conversation id */
    String getConversationId();
}
