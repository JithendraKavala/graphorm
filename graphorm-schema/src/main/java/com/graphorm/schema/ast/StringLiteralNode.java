package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * String literal AST node (e.g. "postgresql", "students").
 */
public record StringLiteralNode(
        SourcePosition position,
        String value
) implements ExpressionNode {

    public StringLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitStringLiteral(this);
    }
}
