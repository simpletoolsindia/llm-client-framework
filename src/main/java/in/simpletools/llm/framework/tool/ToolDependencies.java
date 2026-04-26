package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

/**
 * Declares that a tool depends on other tools completing first.
 * Used to enforce sequential execution when parallel would be incorrect.
 *
 * <pre>
 * {@code
 * @LLMTool(name = "deploy", description = "Deploy application")
 * @ToolDependencies(dependsOn = {"validate_config", "run_tests"})
 * public String deploy(...) { }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolDependencies {
    /** @return tool names that must complete before this tool runs */
    String[] dependsOn() default {};

    /** @return whether to fail this tool when any dependency fails */
    boolean failOnError() default true;
}
