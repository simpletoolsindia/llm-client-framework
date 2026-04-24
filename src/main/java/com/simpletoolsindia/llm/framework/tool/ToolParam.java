package com.simpletoolsindia.llm.framework.tool;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String name() default "";
    String description() default "";
    boolean required() default true;
}