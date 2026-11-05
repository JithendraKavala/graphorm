package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;

/**
 * AST node representing a view's {@code @@groupBy} block attribute declaration.
 *
 * @param position source coordinate
 * @param columns  immutable list of column/expression strings to group by
 */
public record GroupByAttributeNode(
        SourcePosition position,
        List<String> columns
) implements AstNode {

    public GroupByAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitGroupByAttribute(this);
    }
}
