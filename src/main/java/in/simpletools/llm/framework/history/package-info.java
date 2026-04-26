/**
 * Conversation history, persistence, and context-window helpers.
 *
 * <p>{@link in.simpletools.llm.framework.history.ConversationHistory} stores
 * messages in memory. Applications can implement
 * {@link in.simpletools.llm.framework.history.ConversationHistoryStore} to
 * persist history in another backend. {@link in.simpletools.llm.framework.history.TokenTracker}
 * estimates context usage and supports auto-compaction decisions in the client.</p>
 */
package in.simpletools.llm.framework.history;
