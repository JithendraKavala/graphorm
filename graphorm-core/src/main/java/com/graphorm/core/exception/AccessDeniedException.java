package com.graphorm.core.exception;

/**
 * Thrown when an unprivileged user or caller attempts to access a protected model,
 * field, or view requiring specific authorization roles.
 */
public class AccessDeniedException extends GraphOrmException {

    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_ERROR_CODE = "ACCESS_DENIED";

    /**
     * Constructs a new AccessDeniedException with a detail message.
     *
     * @param message the detail message
     */
    public AccessDeniedException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    /**
     * Constructs a new AccessDeniedException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public AccessDeniedException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    /**
     * Constructs a new AccessDeniedException with a custom error code and detail message.
     *
     * @param errorCode the machine-readable error code
     * @param message   the detail message
     */
    public AccessDeniedException(String errorCode, String message) {
        super(errorCode != null ? errorCode : DEFAULT_ERROR_CODE, message);
    }

    /**
     * Constructs a new AccessDeniedException with a custom error code, detail message, and cause.
     *
     * @param errorCode the machine-readable error code
     * @param message   the detail message
     * @param cause     the underlying cause
     */
    public AccessDeniedException(String errorCode, String message, Throwable cause) {
        super(errorCode != null ? errorCode : DEFAULT_ERROR_CODE, message, cause);
    }
}
