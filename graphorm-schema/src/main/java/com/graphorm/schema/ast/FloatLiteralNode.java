package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Floating point literal AST node (e.g. 3.14).
 */
public record FloatLiteralNode(
        SourcePosition position,
        double value,
        String rawText
) implements ExpressionNode {

    public FloatLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
        if (rawText == null) {
            rawText = String.valueOf(value);
        }
    }

    public FloatLiteralNode(SourcePosition position, double value) {
        this(position, value, String.valueOf(value));
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitFloatLiteral(this);
    }
}
