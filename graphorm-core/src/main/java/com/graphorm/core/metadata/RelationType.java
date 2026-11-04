package com.graphorm.core.metadata;

/**
 * Enumeration of supported entity relationship cardinalities.
 */
public enum RelationType {
    /**
     * One-to-one relationship between entities.
     */
    ONE_TO_ONE,

    /**
     * One-to-many relationship between entities.
     */
    ONE_TO_MANY,

    /**
     * Many-to-one relationship between entities.
     */
    MANY_TO_ONE,

    /**
     * Many-to-many relationship between entities (typically mediated by a join table).
     */
    MANY_TO_MANY
}
