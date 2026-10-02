package com.petroad.backend.api;

import com.petroad.backend.security.LoginRateLimitException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String message) {}

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> bad(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted().collect(java.util.stream.Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> unauthorized(AuthenticationFailedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(LoginRateLimitException.class)
    ResponseEntity<ApiError> limited(LoginRateLimitException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(new ApiError(exception.getMessage()));
    }
}
