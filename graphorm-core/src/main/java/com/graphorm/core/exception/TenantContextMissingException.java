package com.graphorm.core.exception;

/**
 * Exception thrown when an operation targets a multi-tenant model or view without an active tenant context.
 */
public class TenantContextMissingException extends GraphOrmException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ERROR_CODE = "ERR_TENANT_CONTEXT_MISSING";
    private final String entityName;
    private final String operation;

    /**
     * Constructs a new TenantContextMissingException.
     *
     * @param entityName the multi-tenant entity name
     * @param operation  the attempted operation (e.g. "SELECT", "INSERT", "UPDATE", "DELETE")
     */
    public TenantContextMissingException(String entityName, String operation) {
        super(
                DEFAULT_ERROR_CODE,
                "Tenant context is required for " + operation + " on multi-tenant entity '" + entityName + "', but none was provided."
        );
        this.entityName = entityName;
        this.operation = operation;
    }

    /**
     * Constructs a new TenantContextMissingException with a custom message.
     *
     * @param entityName the entity name
     * @param operation  the attempted operation
     * @param message    custom detailed error message
     */
    public TenantContextMissingException(String entityName, String operation, String message) {
        super(DEFAULT_ERROR_CODE, message);
        this.entityName = entityName;
        this.operation = operation;
    }

    /**
     * Returns the name of the multi-tenant entity that was targeted.
     *
     * @return the entity name
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Returns the operation that was attempted.
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
}
