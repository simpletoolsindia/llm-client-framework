/**
 * High-level client API for chat, streaming, tools, and conversation state.
 *
 * <p>Most applications only need {@link in.simpletools.llm.framework.client.LLMClient}.
 * It accepts user messages, builds provider requests, handles model tool calls,
 * executes registered Java tools, and stores conversation history.</p>
 *
 * <h2>Quick start</h2>
 *
 * <pre>{@code
 * try (LLMClient client = LLMClient.ollama("gemma4:latest")) {
 *     client.registerTools(new MyTools());
 *     String reply = client.chat("Use my tool and summarize the result.");
 * }
 * }</pre>
 *
 * <h2>Tool registration options</h2>
 *
 * <ul>
 *   <li>{@link in.simpletools.llm.framework.client.LLMClient#tool(String, String, java.util.function.Function)}
 *       for lambda tools</li>
 *   <li>{@link in.simpletools.llm.framework.client.LLMClient#registerTools(Object)}
 *       for annotated service classes</li>
 *   <li>{@link in.simpletools.llm.framework.client.LLMClient#withSystemTools()}
 *       for built-in file, web, and shell tools</li>
 * </ul>
 */
package in.simpletools.llm.framework.client;
