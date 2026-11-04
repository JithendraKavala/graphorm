package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composite type (embedded value object) declaration block (e.g. type Address { street String }).
 *
 * @param position source coordinate
 * @param name     declared composite type name (e.g. "Address")
 * @param fields   list of field declarations
 */
public record CompositeTypeNode(
        SourcePosition position,
        String name,
        List<FieldNode> fields
) implements AstNode {

    public CompositeTypeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        fields = fields == null ? List.of() : List.copyOf(fields);
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

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitCompositeType(this);
    }
}
