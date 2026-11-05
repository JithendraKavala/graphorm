package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * AST node representing an {@code @aggregate(count | sum | avg | min | max: "expression")} field directive.
 *
 * @param position   source coordinate
 * @param function   aggregate function name (e.g. "count", "sum", "avg", "min", "max")
 * @param expression column or expression string to aggregate (e.g. "e.id")
 */
public record AggregateAttributeNode(
        SourcePosition position,
        String function,
        String expression
) implements AstNode {

    public AggregateAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(function, "function must not be null");
        if (function.isBlank()) {
            throw new IllegalArgumentException("function must not be blank");
        }
        Objects.requireNonNull(expression, "expression must not be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitAggregateAttribute(this);
    }
}
