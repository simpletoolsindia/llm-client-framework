package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

/**
 * Marks a tool result as cacheable with configurable TTL.
 * When applied, tool results are stored in Redis/in-memory cache
 * and returned on subsequent identical calls.
 *
 * <pre>
 * {@code
 * @LLMTool(name = "get_weather", description = "Get weather for a city")
 * @Cached(ttlSeconds = 300, keyPrefix = "weather")
 * public String getWeather(@ToolParam("city") String city) {
 *     // expensive API call - result cached for TTL seconds
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cached {
    /**
     * Cache time-to-live.
     *
     * @return cache TTL in seconds
     */
    int ttlSeconds() default 300;

    /**
     * Prefix used for generated cache keys.
     *
     * @return key prefix for cache entries; empty means use the tool name
     */
    String keyPrefix() default "";

    /**
     * Decide whether null tool results should be stored.
     *
     * @return whether null results should be cached
     */
    boolean cacheNull() default false;

    /**
     * Maximum cached entries allowed for one tool.
     *
     * @return maximum cached entries per tool
     */
    int maxEntries() default 1000;
}
