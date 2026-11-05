package com.graphorm.schema.ast;

import com.graphorm.core.query.JoinType;

import java.util.Objects;

/**
 * AST node representing a view's joined model declaration ({@code @@leftJoin} / {@code @@innerJoin}).
 *
 * @param position    source coordinate
 * @param joinType    join type (LEFT or INNER)
 * @param modelName   name of the joined target model
 * @param alias       table/model alias (e.g. "d")
 * @param onCondition join condition expression (e.g. "s.departmentId == d.id")
 */
public record JoinAttributeNode(
        SourcePosition position,
        JoinType joinType,
        String modelName,
        String alias,
        String onCondition
) implements AstNode {

    public JoinAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(joinType, "joinType must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        Objects.requireNonNull(alias, "alias must not be null");
        if (alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        Objects.requireNonNull(onCondition, "onCondition must not be null");
        if (onCondition.isBlank()) {
            throw new IllegalArgumentException("onCondition must not be blank");
        }
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitJoinAttribute(this);
    }
}
