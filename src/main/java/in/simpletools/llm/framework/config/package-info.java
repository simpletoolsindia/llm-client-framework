/**
 * Provider and client configuration types.
 *
 * <p>{@link in.simpletools.llm.framework.config.ClientConfig} is immutable. Each
 * fluent setter returns a new config, which makes it safe to keep a base config
 * and derive provider/model variants from it.</p>
 *
 * <pre>{@code
 * ClientConfig config = ClientConfig.of(Provider.OPENAI)
 *     .model("gpt-4o-mini")
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .temperature(0.2)
 *     .timeoutSeconds(60);
 * }</pre>
 */
package in.simpletools.llm.framework.config;
