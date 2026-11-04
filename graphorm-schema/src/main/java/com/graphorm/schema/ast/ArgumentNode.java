package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Argument in an attribute or function call (named or positional).
 *
 * @param position source coordinate
 * @param name     parameter name (nullable for positional arguments)
 * @param value    argument expression value
 */
public record ArgumentNode(
        SourcePosition position,
        String name,
        ExpressionNode value
) implements AstNode {

    public ArgumentNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ArgumentNode positional(SourcePosition position, ExpressionNode value) {
        return new ArgumentNode(position, null, value);
    }

    public static ArgumentNode named(SourcePosition position, String name, ExpressionNode value) {
        Objects.requireNonNull(name, "Argument name must not be null for named arguments");
        return new ArgumentNode(position, name, value);
    }

    public boolean isNamed() {
        return name != null && !name.isBlank();
    }

    public boolean isPositional() {
        return !isNamed();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitArgument(this);
    }
}
