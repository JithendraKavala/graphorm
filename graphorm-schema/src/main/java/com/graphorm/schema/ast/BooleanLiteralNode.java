package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Boolean literal AST node (true / false).
 */
public record BooleanLiteralNode(
        SourcePosition position,
        boolean value
) implements ExpressionNode {

    public BooleanLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitBooleanLiteral(this);
    }
}
