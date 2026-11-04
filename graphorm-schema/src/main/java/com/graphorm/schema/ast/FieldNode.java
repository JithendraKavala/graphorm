package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Field declaration AST node inside a model, view, or composite type.
 *
 * @param position   source coordinate
 * @param name       declared field name
 * @param type       declared field type
 * @param attributes list of field attributes
 */
public record FieldNode(
        SourcePosition position,
        String name,
        TypeNode type,
        List<FieldAttributeNode> attributes
) implements AstNode {

    public FieldNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public boolean isPrimaryKey() {
        return hasAttribute("id");
    }

    public boolean isOptional() {
        return type.isOptional();
    }

    public boolean isList() {
        return type.isList();
    }

    public boolean isUnique() {
        return hasAttribute("unique");
    }

    public boolean hasAttribute(String attributeName) {
        return findAttribute(attributeName).isPresent();
    }

    public Optional<FieldAttributeNode> findAttribute(String attributeName) {
        if (attributeName == null) {
            return Optional.empty();
        }
        return attributes.stream()
                .filter(a -> attributeName.equals(a.name()))
                .findFirst();
    }

    public Optional<FieldAttributeNode> relationAttribute() {
        return findAttribute("relation");
    }

    public Optional<FieldAttributeNode> aggregateAttribute() {
        return findAttribute("aggregate");
    }

    public Optional<FieldAttributeNode> expressionAttribute() {
        return findAttribute("expression");
    }

    public Optional<FieldAttributeNode> defaultAttribute() {
        return findAttribute("default");
    }

    public Optional<FieldAttributeNode> mapAttribute() {
        return findAttribute("map");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitField(this);
    }
}
