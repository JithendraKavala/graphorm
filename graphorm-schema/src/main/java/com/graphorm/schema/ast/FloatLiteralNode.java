package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Floating point literal AST node (e.g. 3.14).
 */
public record FloatLiteralNode(
        SourcePosition position,
        double value
) implements ExpressionNode {

    public FloatLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitFloatLiteral(this);
    }
}
