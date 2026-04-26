/**
 * Top-level facade package for the SimpleTools LLM Client Framework.
 *
 * <p>The framework gives Java applications one consistent API for local and
 * cloud LLM providers. The most common entry point is
 * {@link in.simpletools.llm.framework.client.LLMClient}; this package also
 * exposes {@link in.simpletools.llm.framework.LLMFramework}, a small convenience
 * facade for applications that prefer a minimal wrapper.</p>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * LLMClient client = LLMClient.ollama("gemma4:latest");
 * String reply = client.chat("Explain Java records in simple terms.");
 * }</pre>
 *
 * <h2>Main packages</h2>
 *
 * <ul>
 *   <li>{@code client}: high-level chat, streaming, history, and tool orchestration</li>
 *   <li>{@code config}: provider selection, model names, API keys, and timeouts</li>
 *   <li>{@code model}: provider-neutral request, response, message, and tool-call records</li>
 *   <li>{@code tool}: annotations and registry classes for function calling</li>
 *   <li>{@code tools}: built-in file, shell, web, and HTTP tools</li>
 *   <li>{@code history}: in-memory and pluggable conversation history support</li>
 *   <li>{@code adapter}: provider adapters used internally and by advanced extensions</li>
 * </ul>
 */
package in.simpletools.llm.framework;
