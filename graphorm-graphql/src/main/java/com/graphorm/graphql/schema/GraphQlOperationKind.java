package com.graphorm.graphql.schema;

/**
 * Architectural classification of GraphQL root operations discovered and bound by GraphORM.
 */
public enum GraphQlOperationKind {
    /**
     * Root query returning a paginated, filtered, sorted list of regular models.
     */
    MODEL_LIST_QUERY,

    /**
     * Root query returning a single regular model by primary key or unique argument.
     */
    MODEL_SINGLE_QUERY,

    /**
     * Root query returning a paginated, filtered, sorted list of view model entities.
     */
    VIEW_LIST_QUERY,

    /**
     * Root query returning a single view model entity.
     */
    VIEW_SINGLE_QUERY,

    /**
     * Root mutation creating a regular model entity.
     */
    MODEL_CREATE_MUTATION,

    /**
     * Root mutation updating a regular model entity by primary key.
     */
    MODEL_UPDATE_MUTATION,

    /**
     * Root mutation deleting a regular model entity by primary key.
     */
    MODEL_DELETE_MUTATION,

    /**
     * Attempted mutation targeting a read-only view model (strictly rejected).
     */
    VIEW_MUTATION_REJECTED
}
