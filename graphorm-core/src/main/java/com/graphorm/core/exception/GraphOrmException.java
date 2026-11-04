package com.graphorm.core.exception;

/**
 * Root unchecked domain exception for all errors occurring within the GraphORM framework.
 *
 * <p>Supports standard exception chaining (message, cause) along with an optional machine-readable
 * error code (e.g., {@code "GRAPHORM_CONFIG_ERROR"}) that can be mapped cleanly to GraphQL error
 * extensions or API responses without exposing internal implementation details.</p>
 */
public class GraphOrmException extends RuntimeException {

    private final String errorCode;

    /**
     * Constructs a new {@code GraphOrmException} with the specified detail message.
     *
     * @param message the detail message describing the error condition
     */
    public GraphOrmException(String message) {
        this(null, message, null);
    }

    /**
     * Constructs a new {@code GraphOrmException} with the specified detail message and cause.
     *
     * @param message the detail message describing the error condition
     * @param cause the underlying cause of this exception
     */
    public GraphOrmException(String message, Throwable cause) {
        this(null, message, cause);
    }

    /**
     * Constructs a new {@code GraphOrmException} with a specific error code and detail message.
     *
     * @param errorCode the machine-readable error code identifying the category of error, or null
     * @param message the detail message describing the error condition
     */
    public GraphOrmException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * Constructs a new {@code GraphOrmException} with a specific error code, detail message, and cause.
     *
     * @param errorCode the machine-readable error code identifying the category of error, or null
     * @param message the detail message describing the error condition
     * @param cause the underlying cause of this exception
     */
    public GraphOrmException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the optional machine-readable error code associated with this exception.
     *
     * @return the error code string, or {@code null} if no code was assigned
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Indicates whether this exception has a non-blank error code.
     *
     * @return {@code true} if an error code is present and non-blank; {@code false} otherwise
     */
    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.isBlank();
    }
}
