package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;

/**
 * Qualified identifier AST node representing dot-separated path components (e.g. s.id, d.name).
 */
public record QualifiedIdentifierNode(
        SourcePosition position,
        List<String> parts
) implements ExpressionNode {

    public QualifiedIdentifierNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(parts, "parts must not be null");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Qualified identifier parts must not be empty");
        }
        parts = List.copyOf(parts);
    }

    public String asQualifiedString() {
        return String.join(".", parts);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitQualifiedIdentifier(this);
    }
}
