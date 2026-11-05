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
    MANY_TO_MANY;

    /**
     * Checks if this relationship cardinality targets at most one related entity.
     *
     * @return true for ONE_TO_ONE and MANY_TO_ONE, false otherwise
     */
    public boolean isToOne() {
        return this == ONE_TO_ONE || this == MANY_TO_ONE;
    }

    /**
     * Checks if this relationship cardinality targets a collection of entities.
     *
     * @return true for ONE_TO_MANY and MANY_TO_MANY, false otherwise
     */
    public boolean isToMany() {
        return this == ONE_TO_MANY || this == MANY_TO_MANY;
    }
}
