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
    /** Cache TTL in seconds. Default 5 minutes. */
    int ttlSeconds() default 300;

    /** Key prefix for cache entries. Defaults to tool name. */
    String keyPrefix() default "";

    /** Whether to cache null results. Default false. */
    boolean cacheNull() default false;

    /** Maximum cached entries per tool. Default 1000. */
    int maxEntries() default 1000;
}