package com.graphorm.core.exception;

/**
 * Exception thrown when GraphORM encounters invalid, missing, or conflicting configuration parameters.
 *
 * <p>Extends {@link GraphOrmException} to preserve the unified domain exception hierarchy.</p>
 */
public class ConfigurationException extends GraphOrmException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code ConfigurationException} with the specified detail message.
     *
     * @param message the detail message explaining the configuration error
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ConfigurationException} with the specified detail message and cause.
     *
     * @param message the detail message explaining the configuration error\n     * @param cause the underlying cause of the configuration failure
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code ConfigurationException} with an error code and detail message.
     *
     * @param errorCode the machine-readable error code
     * @param message the detail message explaining the configuration error
     */
    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Constructs a new {@code ConfigurationException} with an error code, detail message, and cause.
     *
     * @param errorCode the machine-readable error code
     * @param message the detail message explaining the configuration error
     * @param cause the underlying cause of the configuration failure
     */
    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
