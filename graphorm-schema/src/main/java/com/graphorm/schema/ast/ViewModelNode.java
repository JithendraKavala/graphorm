package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * View model declaration block representing a read-only projection (database-backed or virtual).
 *
 * @param position   source coordinate
 * @param name       declared view model name (e.g. "StudentDashboard")
 * @param fields     list of field declarations
 * @param attributes list of block attributes (e.g. @@view, @@map, @@from, @@leftJoin, @@groupBy)
 */
public record ViewModelNode(
        SourcePosition position,
        String name,
        List<FieldNode> fields,
        List<BlockAttributeNode> attributes
) implements AstNode {

    public ViewModelNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        fields = fields == null ? List.of() : List.copyOf(fields);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public boolean isDatabaseBacked() {
        return hasAttribute("view");
    }

    public Optional<FieldNode> findField(String fieldName) {
        if (fieldName == null) {
            return Optional.empty();
        }
        return fields.stream()
                .filter(f -> fieldName.equals(f.name()))
                .findFirst();
    }

    public boolean hasField(String fieldName) {
        return findField(fieldName).isPresent();
    }

    public Optional<BlockAttributeNode> findAttribute(String attributeName) {
        if (attributeName == null) {
            return Optional.empty();
        }
        return attributes.stream()
                .filter(a -> attributeName.equals(a.name()))
                .findFirst();
    }

    public boolean hasAttribute(String attributeName) {
        return findAttribute(attributeName).isPresent();
    }

    public Optional<BlockAttributeNode> mapAttribute() {
        return findAttribute("map");
    }

    public Optional<BlockAttributeNode> fromAttribute() {
        return findAttribute("from");
    }

    public List<BlockAttributeNode> joinAttributes() {
        return attributes.stream()
                .filter(a -> "leftJoin".equals(a.name()) || "innerJoin".equals(a.name()))
                .toList();
    }

    public Optional<BlockAttributeNode> groupByAttribute() {
        return findAttribute("groupBy");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitViewModel(this);
    }
}
