/**
 * Provider adapter layer.
 *
 * <p>Adapters translate the framework's provider-neutral model records into
 * provider-specific HTTP requests and parse provider responses back into
 * {@link in.simpletools.llm.framework.model.LLMResponse}. Most application code
 * should use {@link in.simpletools.llm.framework.client.LLMClient}; implement
 * {@link in.simpletools.llm.framework.adapter.ProviderAdapter} only when adding a
 * new provider or custom gateway.</p>
 */
package in.simpletools.llm.framework.adapter;
