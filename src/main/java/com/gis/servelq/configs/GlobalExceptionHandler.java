package com.gis.servelq.configs;

import com.gis.servelq.dto.ApiResponseDTO;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Handle validation errors from @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO<?>> handleValidationError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request data");
        return ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, message, null));
    }

    // Handle @Validated constraint violations (if using Validator directly)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseDTO<?>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .findFirst()
                .orElse("Invalid request data");
        return ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, message, null));
    }

    // Handle other RuntimeExceptions (like your own)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseDTO<?>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ApiResponseDTO<>(false, ex.getMessage(), null));
    }
}

