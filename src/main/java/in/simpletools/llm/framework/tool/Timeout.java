package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

/**
 * Specifies per-tool execution timeout.
 * Tools exceeding this limit are terminated and return an error.
 *
 * <pre>
 * {@code
 * @LLMTool(name = "slow_api_call")
 * @Timeout(millis = 5000)  // 5 second timeout
 * public String slowCall(...) { }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timeout {
    /** Timeout in milliseconds. Default 30 seconds. */
    long millis() default 30_000;
}