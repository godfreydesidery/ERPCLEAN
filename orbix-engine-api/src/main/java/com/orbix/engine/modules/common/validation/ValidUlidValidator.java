package com.orbix.engine.modules.common.validation;

import com.orbix.engine.modules.common.util.UidGenerator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidUlidValidator implements ConstraintValidator<ValidUlid, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || UidGenerator.isValid(value);
    }
}
