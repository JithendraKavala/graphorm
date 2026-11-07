package com.graphorm.schema.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Field declaration AST node inside a model, view, or composite type.
 *
 * @param position   source coordinate
 * @param name       declared field name
 * @param type       declared field type
 * @param attributes list of field attributes
 */
public record FieldNode(
        SourcePosition position,
        String name,
        TypeNode type,
        List<FieldAttributeNode> attributes
) implements AstNode {

    public FieldNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public boolean isPrimaryKey() {
        return hasAttribute("id");
    }

    public boolean isOptional() {
        return type.isOptional();
    }

    public boolean isList() {
        return type.isList();
    }

    public boolean isUnique() {
        return hasAttribute("unique");
    }

    public boolean isAggregate() {
        return hasAttribute("aggregate");
    }

    public boolean isExpression() {
        return hasAttribute("expression");
    }

    public boolean hasAttribute(String attributeName) {
        return findAttribute(attributeName).isPresent();
    }

    public Optional<FieldAttributeNode> findAttribute(String attributeName) {
        if (attributeName == null) {
            return Optional.empty();
        }
        return attributes.stream()
                .filter(a -> attributeName.equals(a.name()))
                .findFirst();
    }

    public Optional<FieldAttributeNode> relationAttribute() {
        return findAttribute("relation");
    }

    public Optional<FieldAttributeNode> aggregateAttribute() {
        return findAttribute("aggregate");
    }

    public Optional<FieldAttributeNode> expressionAttribute() {
        return findAttribute("expression");
    }

    public Optional<FieldAttributeNode> defaultAttribute() {
        return findAttribute("default");
    }

    public Optional<FieldAttributeNode> mapAttribute() {
        return findAttribute("map");
    }

    public Optional<FieldAttributeNode> authAttribute() {
        return findAttribute("auth");
    }

    /**
     * Extracts declared authorization roles from {@code @auth(roles: [...])} or {@code @auth("ADMIN")}.
     *
     * @return list of role strings (e.g. ["ADMIN", "FINANCE"]) or empty list if not present
     */
    public List<String> authRoles() {
        Optional<FieldAttributeNode> authOpt = authAttribute();
        if (authOpt.isEmpty()) {
            return List.of();
        }
        FieldAttributeNode auth = authOpt.get();
        Optional<ExpressionNode> rolesArg = auth.getArgumentValue("roles")
                .or(() -> auth.getPositionalArgumentValue(0));

        if (rolesArg.isEmpty()) {
            return List.of();
        }

        ExpressionNode expr = rolesArg.get();
        if (expr instanceof ArrayLiteralNode array) {
            List<String> roles = new ArrayList<>();
            for (ExpressionNode elem : array.elements()) {
                String val = extractString(elem);
                if (val != null && !val.isBlank()) {
                    roles.add(val.trim());
                }
            }
            return List.copyOf(roles);
        } else {
            String val = extractString(expr);
            if (val != null && !val.isBlank()) {
                return List.of(val.trim());
            }
        }
        return List.of();
    }

    /**
     * Parses and returns the typed {@link AggregateAttributeNode} if {@code @aggregate} is declared.
     *
     * @return typed AggregateAttributeNode or empty if not present or malformed
     */
    public Optional<AggregateAttributeNode> aggregateClause() {
        return aggregateAttribute().flatMap(attr -> {
            // Check named argument like @aggregate(count: "e.id")
            for (ArgumentNode arg : attr.arguments()) {
                if (arg.isNamed()) {
                    String func = arg.name();
                    String expr = extractString(arg.value());
                    if (expr != null) {
                        return Optional.of(new AggregateAttributeNode(attr.position(), func, expr));
                    }
                }
            }
            // Check positional arguments: @aggregate("count", "e.id")
            Optional<ExpressionNode> first = attr.getPositionalArgumentValue(0);
            Optional<ExpressionNode> second = attr.getPositionalArgumentValue(1);
            if (first.isPresent() && second.isPresent()) {
                String func = extractString(first.get());
                String expr = extractString(second.get());
                if (func != null && expr != null) {
                    return Optional.of(new AggregateAttributeNode(attr.position(), func, expr));
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Parses and returns the typed {@link ExpressionAttributeNode} if {@code @expression} is declared.
     *
     * @return typed ExpressionAttributeNode or empty if not present or malformed
     */
    public Optional<ExpressionAttributeNode> expressionClause() {
        return expressionAttribute().flatMap(attr -> {
            Optional<ExpressionNode> exprNode = attr.getPositionalArgumentValue(0)
                    .or(() -> attr.getArgumentValue("value"))
                    .or(() -> attr.getArgumentValue("expression"));
            if (exprNode.isPresent()) {
                String expr = extractString(exprNode.get());
                if (expr != null && !expr.isBlank()) {
                    return Optional.of(new ExpressionAttributeNode(attr.position(), expr));
                }
            }
            return Optional.empty();
        });
    }

    private static String extractString(ExpressionNode expr) {
        if (expr instanceof StringLiteralNode str) {
            return str.value();
        } else if (expr instanceof IdentifierNode ident) {
            return ident.name();
        } else if (expr instanceof QualifiedIdentifierNode qid) {
            return qid.asQualifiedString();
        }
        return expr != null ? expr.toString() : null;
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitField(this);
    }
}
