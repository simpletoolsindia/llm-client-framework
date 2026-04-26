package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

/**
 * Legacy annotation for Ollama-oriented tool registration.
 *
 * <p>Prefer {@link LLMTool} for new code. This annotation remains supported so
 * older applications can continue to auto-register tool methods.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OllamaTool {
    /** @return tool name exposed to the model; defaults to method name */
    String name() default "";
    /** @return tool description exposed to the model */
    String description() default "";
}
