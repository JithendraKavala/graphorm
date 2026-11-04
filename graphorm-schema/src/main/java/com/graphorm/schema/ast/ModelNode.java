package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Model declaration block representing a physical database table with CRUD capabilities.
 *
 * @param position   source coordinate
 * @param name       declared model name (e.g. "Student")
 * @param fields     list of field declarations
 * @param attributes list of block attributes (e.g. @@id, @@map, @@unique, @@index)
 */
public record ModelNode(
        SourcePosition position,
        String name,
        List<FieldNode> fields,
        List<BlockAttributeNode> attributes
) implements AstNode {

    public ModelNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        fields = fields == null ? List.of() : List.copyOf(fields);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
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

    public List<FieldNode> primaryKeyFields() {
        return fields.stream()
                .filter(FieldNode::isPrimaryKey)
                .toList();
    }

    public Optional<BlockAttributeNode> primaryKeyBlockAttribute() {
        return findAttribute("id");
    }

    public Optional<BlockAttributeNode> mapAttribute() {
        return findAttribute("map");
    }

    public List<BlockAttributeNode> uniqueBlockAttributes() {
        return attributes.stream()
                .filter(a -> "unique".equals(a.name()))
                .toList();
    }

    public List<BlockAttributeNode> indexBlockAttributes() {
        return attributes.stream()
                .filter(a -> "index".equals(a.name()))
                .toList();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitModel(this);
    }
}
