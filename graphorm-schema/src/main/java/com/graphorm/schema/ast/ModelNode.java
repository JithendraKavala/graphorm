package com.graphorm.schema.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.graphorm.core.metadata.ExposureMetadata;

/**
 * Model declaration block representing a physical database table with CRUD capabilities
 * (or read-only projection if annotated with @@readonly).
 *
 * @param position   source coordinate
 * @param name       declared model name (e.g. "Student")
 * @param fields     list of field declarations
 * @param attributes list of block attributes (e.g. @@id, @@map, @@unique, @@index, @@check, @@schema, @@auth, @@readonly)
 */
public record ModelNode(
        SourcePosition position,
        String name,
        List<FieldNode> fields,
        List<BlockAttributeNode> attributes
) implements AstNode {

    public ModelNode {
        Objects.requireNonNull(position, "position must not be null");
        Objects.requireNonNull(name, "name must not be null");
        fields = fields == null ? List.of() : List.copyOf(fields);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
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

    public boolean isReadOnly() {
        return hasAttribute("readonly");
    }

    public Optional<BlockAttributeNode> readonlyAttribute() {
        return findAttribute("readonly");
    }

    public Optional<BlockAttributeNode> authAttribute() {
        return findAttribute("auth");
    }

    public Optional<BlockAttributeNode> exposeAttribute() {
        return findAttribute("expose");
    }

    public ExposureMetadata exposure() {
        Optional<BlockAttributeNode> attrOpt = exposeAttribute();
        if (attrOpt.isEmpty()) {
            return ExposureMetadata.of(true, !isReadOnly());
        }
        BlockAttributeNode attr = attrOpt.get();
        Optional<ExpressionNode> pos0 = attr.getPositionalArgumentValue(0);
        if (pos0.isPresent() && pos0.get() instanceof BooleanLiteralNode boolNode) {
            boolean exp = boolNode.value();
            return exp ? ExposureMetadata.all() : ExposureMetadata.none();
        }
        boolean queries = attr.getArgumentValue("queries")
                .filter(BooleanLiteralNode.class::isInstance)
                .map(e -> ((BooleanLiteralNode) e).value())
                .orElse(true);
        boolean mutations = attr.getArgumentValue("mutations")
                .filter(BooleanLiteralNode.class::isInstance)
                .map(e -> ((BooleanLiteralNode) e).value())
                .orElse(!isReadOnly());
        return ExposureMetadata.of(queries, mutations && !isReadOnly());
    }

    /**
     * Extracts declared authorization roles from {@code @@auth(roles: [...])} or {@code @@auth("ADMIN")}.
     *
     * @return list of role strings (e.g. ["ADMIN", "MANAGER"]) or empty list if not present
     */
    public List<String> authRoles() {
        Optional<BlockAttributeNode> authOpt = authAttribute();
        if (authOpt.isEmpty()) {
            return List.of();
        }
        BlockAttributeNode auth = authOpt.get();
        Optional<ExpressionNode> rolesArg = auth.getArgumentValue("roles")
                .or(() -> auth.getPositionalArgumentValue(0));

        if (rolesArg.isEmpty()) {
            return List.of();
        }

        ExpressionNode expr = rolesArg.get();
        if (expr instanceof ArrayLiteralNode array) {
            List<String> roles = new ArrayList<>();
            for (ExpressionNode elem : array.elements()) {
                String val = extractStringOrIdent(elem);
                if (val != null && !val.isBlank()) {
                    roles.add(val.trim());
                }
            }
            return List.copyOf(roles);
        } else {
            String val = extractStringOrIdent(expr);
            if (val != null && !val.isBlank()) {
                return List.of(val.trim());
            }
        }
        return List.of();
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

    public List<FieldNode> primaryKeyFields() {
        return fields.stream()
                .filter(FieldNode::isPrimaryKey)
                .toList();
    }

    public Optional<BlockAttributeNode> primaryKeyBlockAttribute() {
        return findAttribute("id");
    }

    public Optional<BlockAttributeNode> mapAttribute() {
        return findAttribute("map");
    }

    public Optional<BlockAttributeNode> schemaAttribute() {
        return findAttribute("schema");
    }

    public List<BlockAttributeNode> uniqueBlockAttributes() {
        return attributes.stream()
                .filter(a -> "unique".equals(a.name()))
                .toList();
    }

    public List<BlockAttributeNode> indexBlockAttributes() {
        return attributes.stream()
                .filter(a -> "index".equals(a.name()))
                .toList();
    }

    public List<BlockAttributeNode> checkBlockAttributes() {
        return attributes.stream()
                .filter(a -> "check".equals(a.name()))
                .toList();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitModel(this);
    }
}
