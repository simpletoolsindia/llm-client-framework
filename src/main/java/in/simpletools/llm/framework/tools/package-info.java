/**
 * Built-in tools that can be registered with an LLM client.
 *
 * <p>System tools provide file operations, directory listing, grep, web search,
 * webpage fetch, and shell execution. HTTP tools provide common REST operations.
 * Only register tools that your application really needs, especially when
 * prompts may come from untrusted users.</p>
 *
 * <pre>{@code
 * LLMClient client = LLMClient.ollama("gemma4:latest")
 *     .withSystemTools("web")
 *     .withHttpTools();
 * }</pre>
 */
package in.simpletools.llm.framework.tools;
