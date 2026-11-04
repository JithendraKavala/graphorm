package com.graphorm.core.metadata;

/**
 * Categorizes the architectural entity primitives supported by GraphORM schemas.
 */
public enum EntityType {
    /**
     * Regular model mapping 1:1 to a physical database table with full CRUD capabilities.
     */
    MODEL,

    /**
     * Read-only projection combining multiple models, joins, or aggregations.
     */
    VIEW,

    /**
     * Reusable structural value object without an independent database identity.
     */
    COMPOSITE_TYPE
}
