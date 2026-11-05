package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * AST node representing a view's root source model declaration ({@code @@from(Model, as: "alias")}).
 *
 * @param position  source coordinate
 * @param modelName name of the source model
 * @param alias     table/model alias (e.g. "s")
 */
public record FromAttributeNode(
        SourcePosition position,
        String modelName,
        String alias
) implements AstNode {

    public FromAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("modelName must not be blank");
        }
        Objects.requireNonNull(alias, "alias must not be null");
        if (alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitFromAttribute(this);
    }
}
