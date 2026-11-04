package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Identifier AST node (e.g. Student, id).
 */
public record IdentifierNode(
        SourcePosition position,
        String name
) implements ExpressionNode {

    public IdentifierNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitIdentifier(this);
    }
}
