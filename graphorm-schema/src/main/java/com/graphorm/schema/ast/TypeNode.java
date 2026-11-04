package com.graphorm.schema.ast;

import java.util.Objects;

/**
 * Type declaration AST node for model/view/type fields.
 *
 * @param position   source position
 * @param name       type name (e.g. "String", "Int", "Student", "Address")
 * @param isOptional true if marked with ?
 * @param isList     true if marked with []
 */
public record TypeNode(
        SourcePosition position,
        String name,
        boolean isOptional,
        boolean isList
) implements AstNode {

    public TypeNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "type name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Type name must not be blank");
        }
    }

    public static TypeNode required(SourcePosition position, String name) {
        return new TypeNode(position, name, false, false);
    }

    public static TypeNode optional(SourcePosition position, String name) {
        return new TypeNode(position, name, true, false);
    }

    public static TypeNode list(SourcePosition position, String name) {
        return new TypeNode(position, name, false, true);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitType(this);
    }

    @Override
    public String toString() {
        return name + (isList ? "[]" : "") + (isOptional ? "?" : "");
    }
}
