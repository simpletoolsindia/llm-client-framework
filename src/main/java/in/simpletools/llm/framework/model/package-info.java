/**
 * Provider-neutral data model used by clients and adapters.
 *
 * <p>These records represent chat messages, requests, responses, tool
 * definitions, and tool calls. Application code can use them directly for
 * advanced workflows, but most users interact with them through
 * {@link in.simpletools.llm.framework.client.LLMClient}.</p>
 *
 * <h2>Roles and tool flow</h2>
 *
 * <p>A normal tool-calling turn contains a user message, an assistant message
 * with one or more {@link in.simpletools.llm.framework.model.ToolCall}s, one
 * tool message per result, and then a final assistant response.</p>
 */
package in.simpletools.llm.framework.model;
