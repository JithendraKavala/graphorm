package com.graphorm.core.exception;

/**
 * Exception thrown when a write operation (insert, update, delete) targets
 * a read-only entity, such as a database-backed or GraphORM-defined view model.
 */
public class ReadOnlyViewException extends GraphOrmException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ERROR_CODE = "ERR_READ_ONLY_VIEW";
    private final String entityName;
    private final String operation;

    /**
     * Constructs a new ReadOnlyViewException.
     *
     * @param entityName the name of the read-only view or entity
     * @param operation  the attempted write operation (e.g. "INSERT", "UPDATE", "DELETE")
     */
    public ReadOnlyViewException(String entityName, String operation) {
        super(
                DEFAULT_ERROR_CODE,
                "Cannot perform " + operation + " on read-only view '" + entityName + "'. Views are strictly read-only."
        );
        this.entityName = entityName;
        this.operation = operation;
    }

    /**
     * Constructs a new ReadOnlyViewException with a custom message.
     *
     * @param entityName the name of the read-only view or entity
     * @param operation  the attempted write operation
     * @param message    custom detailed error message
     */
    public ReadOnlyViewException(String entityName, String operation, String message) {
        super(DEFAULT_ERROR_CODE, message);
        this.entityName = entityName;
        this.operation = operation;
    }

    /**
     * Returns the name of the read-only entity that was targeted.
     *
     * @return the entity name
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Returns the write operation that was attempted.
     *
     * @return the operation name
     */
    public String getOperation() {
        return operation;
    }
}
