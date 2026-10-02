package com.petroad.backend.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Enforces the byte boundary even when callers bypass request validation. */
public class BoundedBCryptPasswordEncoder extends BCryptPasswordEncoder {
    @Override
    public String encode(CharSequence rawPassword) {
        if (!PasswordBytesValidator.withinLimit(rawPassword)) {
            throw new IllegalArgumentException("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        return super.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return PasswordBytesValidator.withinLimit(rawPassword) && super.matches(rawPassword, encodedPassword);
    }
}
