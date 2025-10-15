package com.ctm.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String name();
    int length() default 0;              // 0 => ignore (use DB default)
    boolean nullable() default true;
    boolean unique() default false;
    String defaultValue() default "";    // e.g. "0", "CURRENT_TIMESTAMP"
    String type() default "";            // explicitly specify DB column type
}
