package com.graphorm.core.exception;

/**
 * Exception thrown when a requested entity, model, view, composite type,
 * or enum is not found in the {@link com.graphorm.core.metadata.ModelRegistry}.
 */
public class EntityNotFoundException extends GraphOrmException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ERROR_CODE = "ERR_ENTITY_NOT_FOUND";
    private final String entityName;

    /**
     * Constructs a new EntityNotFoundException for the specified entity name.
     *
     * @param entityName the name of the missing entity
     */
    public EntityNotFoundException(String entityName) {
        super(DEFAULT_ERROR_CODE, "Entity '" + entityName + "' not found in ModelRegistry");
        this.entityName = entityName;
    }

    /**
     * Constructs a new EntityNotFoundException with a custom message.
     *
     * @param entityName the name of the missing entity
     * @param message    custom detailed error message
     */
    public EntityNotFoundException(String entityName, String message) {
        super(DEFAULT_ERROR_CODE, message);
        this.entityName = entityName;
    }

    /**
     * Returns the name of the entity that was not found.
     *
     * @return the entity name
     */
    public String getEntityName() {
        return entityName;
    }
}
