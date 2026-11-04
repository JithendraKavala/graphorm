package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Integer literal AST node (e.g. 10, -5).
 */
public record IntLiteralNode(
        SourcePosition position,
        long value
) implements ExpressionNode {

    public IntLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitIntLiteral(this);
    }
}
