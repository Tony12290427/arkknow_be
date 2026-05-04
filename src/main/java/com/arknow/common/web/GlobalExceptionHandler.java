package com.arknow.common.web;

import com.arknow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return buildResponse(ex.getCode().name(), ex.getMessage(), request, httpStatus(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse("BAD_REQUEST", msg, request, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        return buildResponse("INTERNAL_ERROR", "服务异常，请稍后重试", request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(String code, String message, HttpServletRequest request, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, status);
    }

    private HttpStatus httpStatus(BusinessException ex) {
        return switch (ex.getCode()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case IDENTIFIER_EXISTS, IDENTIFIER_NOT_FOUND, TERMS_NOT_ACCEPTED,
                 VERIFICATION_NOT_FOUND, VERIFICATION_MISMATCH, VERIFICATION_TOO_MANY_ATTEMPTS,
                 VERIFICATION_RATE_LIMIT, VERIFICATION_DAILY_LIMIT -> HttpStatus.BAD_REQUEST;
            case INVALID_CREDENTIALS, REFRESH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case PASSWORD_POLICY_VIOLATION -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
