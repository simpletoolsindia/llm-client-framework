package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

/**
 * Marks a method as an LLM-callable tool with auto-registration.
 *
 * <pre>
 * Example:
 * {@code
 * @LLMTool(name = "calculate", description = "Evaluates math", maxRetries = 3)
 * public double calculate(@ToolParam("expr") String expr) {
 *     return eval(expr);
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LLMTool {
    /** @return tool name exposed to the LLM; defaults to method name */
    String name() default "";

    /** @return description of what the tool does; defaults to method name */
    String description() default "";

    /** @return max retry attempts on failure; set to 0 to disable retry */
    int maxRetries() default 3;

    /** @return initial retry delay in milliseconds */
    long retryDelayMs() default 500;

    /** @return backoff multiplier for exponential retry */
    double backoffMultiplier() default 2.0;

    /** @return maximum retry delay in milliseconds */
    long maxRetryDelayMs() default 10000;

    // ===== Cache Configuration (v1.1) =====
    /** @return true to mark this tool as cacheable */
    boolean cached() default false;

    /** @return cache TTL in seconds */
    int cacheTtlSeconds() default 300;

    // ===== Circuit Breaker (v1.1) =====
    /** @return failures before circuit opens; 0 disables circuit breaking */
    int failureThreshold() default 0;

    /** @return milliseconds before attempting circuit reset */
    long circuitResetMs() default 60_000;

    // ===== Timeout (v1.1) =====
    /** @return tool execution timeout in milliseconds; 0 means no timeout */
    long timeoutMs() default 0;
}
