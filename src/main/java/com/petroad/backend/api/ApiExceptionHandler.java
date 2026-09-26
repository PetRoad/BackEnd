package com.petroad.backend.api;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestControllerAdvice public class ApiExceptionHandler { @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));} }
