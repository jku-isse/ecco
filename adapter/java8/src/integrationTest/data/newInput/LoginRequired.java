package com.refugee.login;

import java.lang.annotation.*;

/**
 * Created by Florian on 15.11.2016.
 */
@Target({ElementType.METHOD})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {
    enum Roles {
        STUDENT, ADMIN;
    }

    Roles role() default Roles.STUDENT;
}