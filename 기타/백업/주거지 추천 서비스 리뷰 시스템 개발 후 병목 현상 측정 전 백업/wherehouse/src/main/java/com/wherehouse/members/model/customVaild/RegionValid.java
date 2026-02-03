package com.wherehouse.members.model.customVaild;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD })
@Retention(RUNTIME)
@Constraint(validatedBy = RegionValidator.class)
public @interface RegionValid {
    String message() default "유효하지 않은 지역입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
