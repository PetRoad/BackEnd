package com.petroad.backend.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordBytesValidator implements ConstraintValidator<MaxPasswordBytes, String> {
    static final int MAX_BYTES = 72;

    static boolean withinLimit(CharSequence value) {
        return value != null && value.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || withinLimit(value);
    }
}
