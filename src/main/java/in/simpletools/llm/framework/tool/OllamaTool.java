package in.simpletools.llm.framework.tool;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OllamaTool {
    String name() default "";
    String description() default "";
}