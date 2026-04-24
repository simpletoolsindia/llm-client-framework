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
}
