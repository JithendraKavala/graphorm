package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Enum declaration block (e.g. enum Role { ADMIN USER }).
 *
 * @param position   source coordinate
 * @param name       declared enum name
 * @param values     list of declared enum values
 * @param attributes list of block attributes (e.g. @@map)
 */
public record EnumNode(
        SourcePosition position,
        String name,
        List<EnumValueNode> values,
        List<BlockAttributeNode> attributes
) implements AstNode {

    public EnumNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        values = values == null ? List.of() : List.copyOf(values);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public Optional<EnumValueNode> findValue(String valueName) {
        if (valueName == null) {
            return Optional.empty();
        }
        return values.stream()
                .filter(v -> valueName.equals(v.name()))
                .findFirst();
    }

    public boolean hasValue(String valueName) {
        return findValue(valueName).isPresent();
    }

    public Optional<BlockAttributeNode> mapAttribute() {
        return attributes.stream()
                .filter(a -> "map".equals(a.name()))
                .findFirst();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitEnum(this);
    }
}
