package com.petroad.backend.api;

// 로그인 실패(이메일이 없거나 비밀번호가 틀림)를 나타내는 전용 예외.
// IllegalArgumentException과 구분해서 던져야 ApiExceptionHandler에서
// "이건 401로 응답해야 하는 에러"라고 구분할 수 있다.
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
