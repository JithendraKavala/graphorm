package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Function call AST node (e.g. autoincrement(), now(), env("DATABASE_URL")).
 */
public record FunctionCallNode(
        SourcePosition position,
        String functionName,
        List<ArgumentNode> arguments
) implements ExpressionNode {

    public FunctionCallNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(functionName, "functionName must not be null");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public Optional<ArgumentNode> argumentAt(int index) {
        if (index >= 0 && index < arguments.size()) {
            return Optional.of(arguments.get(index));
        }
        return Optional.empty();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitFunctionCall(this);
    }
}
