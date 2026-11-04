package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Value declared inside an enum block.
 *
 * @param position   source coordinate
 * @param name       declared value name
 * @param attributes list of attributes (e.g. @map)
 */
public record EnumValueNode(
        SourcePosition position,
        String name,
        List<FieldAttributeNode> attributes
) implements AstNode {

    public EnumValueNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public Optional<FieldAttributeNode> findAttribute(String attributeName) {
        if (attributeName == null) {
            return Optional.empty();
        }
        return attributes.stream()
                .filter(a -> attributeName.equals(a.name()))
                .findFirst();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitEnumValue(this);
    }
}
