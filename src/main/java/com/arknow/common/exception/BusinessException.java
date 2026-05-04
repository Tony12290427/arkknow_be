package com.arknow.common.exception;

/**
 * A domain-level exception carrying an {@link ErrorCode}.
 * <p>
 * Thrown by service-layer code when a business rule is violated. The {@link GlobalExceptionHandler}
 * catches it and produces a structured JSON error response. This decouples the service layer from
 * HTTP concerns — services throw what went wrong, not how to format it.
 */
public class BusinessException extends RuntimeException {
    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
