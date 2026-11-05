package com.graphorm.schema.ast;

import com.graphorm.core.query.JoinType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * View model declaration block representing a read-only projection (database-backed or virtual).
 *
 * @param position   source coordinate
 * @param name       declared view model name (e.g. "StudentDashboard")
 * @param fields     list of field declarations
 * @param attributes list of block attributes (e.g. @@view, @@map, @@from, @@leftJoin, @@groupBy)
 */
public record ViewModelNode(
        SourcePosition position,
        String name,
        List<FieldNode> fields,
        List<BlockAttributeNode> attributes
) implements AstNode {

    public ViewModelNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        fields = fields == null ? List.of() : List.copyOf(fields);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public boolean isDatabaseBacked() {
        return hasAttribute("view");
    }

    public Optional<FieldNode> findField(String fieldName) {
        if (fieldName == null) {
            return Optional.empty();
        }
        return fields.stream()
                .filter(f -> fieldName.equals(f.name()))
                .findFirst();
    }

    public boolean hasField(String fieldName) {
        return findField(fieldName).isPresent();
    }

    public Optional<BlockAttributeNode> findAttribute(String attributeName) {
        if (attributeName == null) {
            return Optional.empty();
        }
        return attributes.stream()
                .filter(a -> attributeName.equals(a.name()))
                .findFirst();
    }

    public boolean hasAttribute(String attributeName) {
        return findAttribute(attributeName).isPresent();
    }

    public Optional<BlockAttributeNode> mapAttribute() {
        return findAttribute("map");
    }

    public Optional<BlockAttributeNode> fromAttribute() {
        return findAttribute("from");
    }

    public List<BlockAttributeNode> joinAttributes() {
        return attributes.stream()
                .filter(a -> "leftJoin".equals(a.name()) || "innerJoin".equals(a.name()))
                .toList();
    }

    public Optional<BlockAttributeNode> groupByAttribute() {
        return findAttribute("groupBy");
    }

    /**
     * Parses and returns the typed {@link FromAttributeNode} if {@code @@from} is declared.
     *
     * @return typed FromAttributeNode or empty if not present or malformed
     */
    public Optional<FromAttributeNode> fromClause() {
        return fromAttribute().flatMap(from -> {
            Optional<ExpressionNode> modelArg = from.getPositionalArgumentValue(0).or(() -> from.getArgumentValue("model"));
            Optional<ExpressionNode> asArg = from.getArgumentValue("as").or(() -> from.getPositionalArgumentValue(1));
            if (modelArg.isEmpty() || asArg.isEmpty()) {
                return Optional.empty();
            }
            String modelName = extractStringOrIdent(modelArg.get());
            String alias = extractStringOrIdent(asArg.get());
            if (modelName == null || alias == null) {
                return Optional.empty();
            }
            return Optional.of(new FromAttributeNode(from.position(), modelName, alias));
        });
    }

    /**
     * Parses and returns the typed list of {@link JoinAttributeNode}s declared in this view.
     *
     * @return list of typed JoinAttributeNode
     */
    public List<JoinAttributeNode> joinClauses() {
        List<JoinAttributeNode> list = new ArrayList<>();
        for (BlockAttributeNode joinAttr : joinAttributes()) {
            JoinType joinType = "innerJoin".equals(joinAttr.name()) ? JoinType.INNER : JoinType.LEFT;
            Optional<ExpressionNode> modelArg = joinAttr.getPositionalArgumentValue(0).or(() -> joinAttr.getArgumentValue("model"));
            Optional<ExpressionNode> asArg = joinAttr.getArgumentValue("as").or(() -> joinAttr.getPositionalArgumentValue(1));
            Optional<ExpressionNode> onArg = joinAttr.getArgumentValue("on").or(() -> joinAttr.getPositionalArgumentValue(2));

            if (modelArg.isPresent() && asArg.isPresent() && onArg.isPresent()) {
                String modelName = extractStringOrIdent(modelArg.get());
                String alias = extractStringOrIdent(asArg.get());
                String on = extractStringOrIdent(onArg.get());
                if (modelName != null && alias != null && on != null) {
                    list.add(new JoinAttributeNode(joinAttr.position(), joinType, modelName, alias, on));
                }
            }
        }
        return List.copyOf(list);
    }

    /**
     * Parses and returns the typed {@link GroupByAttributeNode} if {@code @@groupBy} is declared.
     *
     * @return typed GroupByAttributeNode or empty if not present
     */
    public Optional<GroupByAttributeNode> groupByClause() {
        return groupByAttribute().map(gb -> {
            List<String> cols = new ArrayList<>();
            Optional<ExpressionNode> argVal = gb.getPositionalArgumentValue(0).or(() -> gb.getArgumentValue("fields"));
            if (argVal.isPresent() && argVal.get() instanceof ArrayLiteralNode array) {
                for (ExpressionNode elem : array.elements()) {
                    String col = extractStringOrIdent(elem);
                    if (col != null) {
                        cols.add(col);
                    }
                }
            }
            return new GroupByAttributeNode(gb.position(), cols);
        });
    }

    private static String extractStringOrIdent(ExpressionNode expr) {
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
        return visitor.visitViewModel(this);
    }
}
