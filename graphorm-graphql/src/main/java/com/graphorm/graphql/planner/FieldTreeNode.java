package com.graphorm.graphql.planner;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable node in a GraphQL selection set field tree.
 * Represents a selected field, its client alias, arguments, and nested children.
 *
 * @param name      the declared GraphQL schema field name
 * @param alias     the client-specified alias, or null if unaliased
 * @param resultKey the key used in the GraphQL result map (alias if present, else name)
 * @param arguments the map of argument values supplied on this field
 * @param children  the child field nodes under this field (e.g. nested relationship fields)
 */
public record FieldTreeNode(
        String name,
        String alias,
        String resultKey,
        Map<String, Object> arguments,
        Map<String, FieldTreeNode> children
) {

    public FieldTreeNode {
        Objects.requireNonNull(name, "Field name must not be null");
        resultKey = resultKey != null ? resultKey : (alias != null ? alias : name);
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(arguments);
        children = children == null ? Map.of() : Collections.unmodifiableMap(children);
    }

    public static FieldTreeNode leaf(String name) {
        return new FieldTreeNode(name, null, name, Map.of(), Map.of());
    }

    public static FieldTreeNode leaf(String name, String alias) {
        return new FieldTreeNode(name, alias, alias != null ? alias : name, Map.of(), Map.of());
    }

    public static FieldTreeNode of(String name, String alias, Map<String, Object> arguments, Map<String, FieldTreeNode> children) {
        return new FieldTreeNode(name, alias, alias != null ? alias : name, arguments, children);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean hasChild(String childName) {
        return children.containsKey(childName);
    }

    public FieldTreeNode child(String childName) {
        return children.get(childName);
    }

    public Set<String> childNames() {
        return children.keySet();
    }
}
