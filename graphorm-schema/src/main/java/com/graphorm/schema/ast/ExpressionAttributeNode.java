package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * AST node representing an {@code @expression("...")} computed expression field directive.
 *
 * @param position   source coordinate
 * @param expression custom SQL expression string (e.g. "AVG(e.grade_points)", "CONCAT(s.first_name, ' ', s.last_name)")
 */
public record ExpressionAttributeNode(
        SourcePosition position,
        String expression
) implements AstNode {

    public ExpressionAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitExpressionAttribute(this);
    }
}
