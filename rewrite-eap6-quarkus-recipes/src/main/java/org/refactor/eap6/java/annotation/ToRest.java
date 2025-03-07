package org.refactor.eap6.java.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToRest {

    String path() default "";

    ActionType action() default ActionType.POST;

    String inputWrapperName() default "";

    String tag() default "";

    String description() default "";


    enum ActionType {
        GET, POST, PUT, DELETE,
    }
}
