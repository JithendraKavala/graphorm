package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Attribute attached to a field (e.g. @id, @map("user_id"), @relation(...)).
 *
 * @param position  source coordinate
 * @param name      attribute name without leading '@' (e.g. "id", "map", "relation")
 * @param arguments list of arguments passed to the attribute
 */
public record FieldAttributeNode(
        SourcePosition position,
        String name,
        List<ArgumentNode> arguments
) implements AstNode {

    public FieldAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "attribute name must not be null");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public Optional<ArgumentNode> findArgument(String paramName) {
        if (paramName == null) {
            return Optional.empty();
        }
        return arguments.stream()
                .filter(a -> paramName.equals(a.name()))
                .findFirst();
    }

    public Optional<ArgumentNode> argumentAt(int index) {
        if (index >= 0 && index < arguments.size()) {
            return Optional.of(arguments.get(index));
        }
        return Optional.empty();
    }

    public Optional<ExpressionNode> getArgumentValue(String paramName) {
        return findArgument(paramName).map(ArgumentNode::value);
    }

    public Optional<ExpressionNode> getPositionalArgumentValue(int index) {
        return argumentAt(index).map(ArgumentNode::value);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitFieldAttribute(this);
    }
}
