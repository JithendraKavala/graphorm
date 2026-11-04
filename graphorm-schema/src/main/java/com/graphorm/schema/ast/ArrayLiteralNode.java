package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;

/**
 * Array literal AST node (e.g. [id, name], ["s.id", "s.name"]).
 */
public record ArrayLiteralNode(
        SourcePosition position,
        List<ExpressionNode> elements
) implements ExpressionNode {

    public ArrayLiteralNode {
        Objects.requireNonNull(position, "position must not be null");
        elements = elements == null ? List.of() : List.copyOf(elements);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitArrayLiteral(this);
    }
}
