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
    /** Tool name exposed to the LLM. Defaults to method name. */
    String name() default "";

    /** Description of what the tool does. Defaults to method name. */
    String description() default "";

    /** Max retry attempts on failure. Default 3. Set to 0 to disable retry. */
    int maxRetries() default 3;

    /** Initial retry delay in milliseconds. Default 500ms. */
    long retryDelayMs() default 500;

    /** Backoff multiplier for exponential retry. Default 2.0. */
    double backoffMultiplier() default 2.0;

    /** Max retry delay in milliseconds. Default 10000ms. */
    long maxRetryDelayMs() default 10000;

    // ===== Cache Configuration (v1.1) =====
    /** Enable caching for tool results. Default false. */
    boolean cached() default false;

    /** Cache TTL in seconds. Default 300 (5 minutes). */
    int cacheTtlSeconds() default 300;

    // ===== Circuit Breaker (v1.1) =====
    /** Failures before circuit opens. 0 = disabled. Default 0. */
    int failureThreshold() default 0;

    /** Milliseconds before attempting circuit reset. Default 60000. */
    long circuitResetMs() default 60_000;

    // ===== Timeout (v1.1) =====
    /** Tool execution timeout in milliseconds. 0 = no timeout. Default 0. */
    long timeoutMs() default 0;
}
