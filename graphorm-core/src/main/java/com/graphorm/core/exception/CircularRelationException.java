package com.graphorm.core.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when a circular relationship loop or infinite traversal is detected during query planning.
 */
public class CircularRelationException extends GraphOrmException {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ERROR_CODE = "ERR_CIRCULAR_RELATION";

    private final ArrayList<String> path;

    /**
     * Constructs a new CircularRelationException with a descriptive message.
     *
     * @param message error description
     */
    public CircularRelationException(String message) {
        super(DEFAULT_ERROR_CODE, message);
        this.path = new ArrayList<>();
    }

    /**
     * Constructs a new CircularRelationException with the cycle path.
     *
     * @param path list of entity or relation names forming the cycle
     */
    public CircularRelationException(List<String> path) {
        super(DEFAULT_ERROR_CODE, "Circular relation loop detected: " + String.join(" -> ", path));
        this.path = path == null ? new ArrayList<>() : new ArrayList<>(path);
    }

    /**
     * Constructs a new CircularRelationException with a custom message and cycle path.
     *
     * @param message custom error message
     * @param path    list of entity or relation names forming the cycle
     */
    public CircularRelationException(String message, List<String> path) {
        super(DEFAULT_ERROR_CODE, message);
        this.path = path == null ? new ArrayList<>() : new ArrayList<>(path);
    }

    /**
     * Returns the traversal path that formed the circular loop.
     *
     * @return immutable list of cycle elements
     */
    public List<String> getPath() {
        return Collections.unmodifiableList(path);
    }
}
