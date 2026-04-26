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
    /** @return parameter name exposed to the LLM; defaults to the Java parameter name */
    String name() default "";

    /** @return human-readable description of the parameter */
    String description() default "";

    /** @return whether this parameter is required */
    boolean required() default true;
}
