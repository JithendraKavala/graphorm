package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;

/**
 * AST node representing a model-level {@code @@notify(on: [CREATE, UPDATE, DELETE])} directive.
 *
 * @param position source coordinate
 * @param events   list of event type names (e.g. "CREATE", "UPDATE", "DELETE")
 */
public record NotifyAttributeNode(
        SourcePosition position,
        List<String> events
) implements AstNode {

    public NotifyAttributeNode {
        Objects.requireNonNull(position, "position must not be null");
        events = events == null ? List.of() : List.copyOf(events);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitNotifyAttribute(this);
    }
}
