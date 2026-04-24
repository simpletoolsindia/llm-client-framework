package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

/**
 * Describes a parameter for an {@link LLMTool}-annotated method.
 *
 * <pre>
 * Example:
 * {@code
 * @LLMTool(name = "search", description = "Search the web")
 * public String search(@ToolParam(name = "query", description = "Search query") String query) {
 *     ...
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    /** Parameter name exposed to the LLM. Defaults to the Java parameter name. */
    String name() default "";

    /** Human-readable description of the parameter. */
    String description() default "";

    /** Whether this parameter is required. Default true. */
    boolean required() default true;
}
