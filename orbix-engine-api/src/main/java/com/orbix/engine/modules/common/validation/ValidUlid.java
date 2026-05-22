package com.orbix.engine.modules.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean-validation constraint for path / query / body fields that must be a
 * 26-char Crockford ULID. Apply to {@code @PathVariable}, {@code @RequestParam}
 * or DTO record components.
 */
@Documented
@Constraint(validatedBy = ValidUlidValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUlid {
    String message() default "must be a 26-character Crockford ULID";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
