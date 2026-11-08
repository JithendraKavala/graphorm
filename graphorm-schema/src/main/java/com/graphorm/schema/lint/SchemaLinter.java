package com.graphorm.schema.lint;

import com.graphorm.schema.ast.*;
import com.graphorm.schema.parser.GraphOrmParserFacade;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Linter for GraphORM schemas that enforces best practices, naming conventions,
 * foreign key indexing rules, and redundant attribute detection.
 */
public final class SchemaLinter {

    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
    private static final Pattern CAMEL_CASE = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern UPPER_SNAKE_CASE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public static final String RULE_NAMING_CONVENTION = "naming-convention";
    public static final String RULE_MISSING_FK_INDEX = "missing-fk-index";
    public static final String RULE_REDUNDANT_ANNOTATION = "redundant-annotation";

    private SchemaLinter() {}

    /**
     * Lints the schema source code and returns diagnostics.
     *
     * @param source the raw schema source
     * @return lint result containing all diagnostics
     */
    public static LintResult lint(String source) {
        if (source == null || source.isBlank()) {
            return LintResult.clean();
        }
        SchemaDocument doc = GraphOrmParserFacade.parse(source);
        return lint(doc);
    }

    /**
     * Lints an existing parsed {@link SchemaDocument}.
     *
     * @param document the parsed AST
     * @return lint result
     */
    public static LintResult lint(SchemaDocument document) {
        Objects.requireNonNull(document, "SchemaDocument must not be null");
        List<LintDiagnostic> diagnostics = new ArrayList<>();

        // 1. Models
        for (ModelNode model : document.models()) {
            checkModelNaming(model, diagnostics);
            checkModelRedundantAttributes(model, diagnostics);
            checkModelFields(model, diagnostics);
            checkForeignKeyIndexes(model, diagnostics);
        }

        // 2. View Models
        for (ViewModelNode view : document.viewModels()) {
            checkViewNaming(view, diagnostics);
            checkViewFields(view, diagnostics);
        }

        // 3. Composite Types
        for (CompositeTypeNode ct : document.compositeTypes()) {
            checkTypeNaming(ct, diagnostics);
            for (FieldNode f : ct.fields()) {
                checkFieldNaming(ct.name(), f, diagnostics);
            }
        }

        // 4. Enums
        for (EnumNode en : document.enums()) {
            checkEnumNaming(en, diagnostics);
            for (EnumValueNode val : en.values()) {
                if (!UPPER_SNAKE_CASE.matcher(val.name()).matches() && !PASCAL_CASE.matcher(val.name()).matches()) {
                    diagnostics.add(LintDiagnostic.warning(
                            RULE_NAMING_CONVENTION,
                            "Enum value '" + val.name() + "' in enum '" + en.name() + "' should be UPPER_SNAKE_CASE (e.g. '" + val.name().toUpperCase() + "')",
                            val.position()
                    ));
                }
            }
        }

        return LintResult.of(diagnostics);
    }

    private static void checkModelNaming(ModelNode model, List<LintDiagnostic> diagnostics) {
        if (!PASCAL_CASE.matcher(model.name()).matches()) {
            diagnostics.add(LintDiagnostic.warning(
                    RULE_NAMING_CONVENTION,
                    "Model name '" + model.name() + "' should be in PascalCase",
                    model.position()
            ));
        }
    }

    private static void checkViewNaming(ViewModelNode view, List<LintDiagnostic> diagnostics) {
        if (!PASCAL_CASE.matcher(view.name()).matches()) {
            diagnostics.add(LintDiagnostic.warning(
                    RULE_NAMING_CONVENTION,
                    "View model name '" + view.name() + "' should be in PascalCase",
                    view.position()
            ));
        }
    }

    private static void checkTypeNaming(CompositeTypeNode ct, List<LintDiagnostic> diagnostics) {
        if (!PASCAL_CASE.matcher(ct.name()).matches()) {
            diagnostics.add(LintDiagnostic.warning(
                    RULE_NAMING_CONVENTION,
                    "Composite type name '" + ct.name() + "' should be in PascalCase",
                    ct.position()
            ));
        }
    }

    private static void checkEnumNaming(EnumNode en, List<LintDiagnostic> diagnostics) {
        if (!PASCAL_CASE.matcher(en.name()).matches()) {
            diagnostics.add(LintDiagnostic.warning(
                    RULE_NAMING_CONVENTION,
                    "Enum name '" + en.name() + "' should be in PascalCase",
                    en.position()
            ));
        }
    }

    private static void checkModelFields(ModelNode model, List<LintDiagnostic> diagnostics) {
        for (FieldNode field : model.fields()) {
            checkFieldNaming(model.name(), field, diagnostics);
            checkFieldRedundantAnnotations(model.name(), field, diagnostics);
        }
    }

    private static void checkViewFields(ViewModelNode view, List<LintDiagnostic> diagnostics) {
        for (FieldNode field : view.fields()) {
            checkFieldNaming(view.name(), field, diagnostics);
        }
    }

    private static void checkFieldNaming(String containerName, FieldNode field, List<LintDiagnostic> diagnostics) {
        if (!CAMEL_CASE.matcher(field.name()).matches()) {
            diagnostics.add(LintDiagnostic.warning(
                    RULE_NAMING_CONVENTION,
                    "Field name '" + field.name() + "' in '" + containerName + "' should be in camelCase",
                    field.position()
            ));
        }
    }

    private static void checkFieldRedundantAnnotations(String modelName, FieldNode field, List<LintDiagnostic> diagnostics) {
        // Redundant @unique on @id
        if (field.isPrimaryKey() && field.isUnique()) {
            diagnostics.add(LintDiagnostic.warning(
                    RULE_REDUNDANT_ANNOTATION,
                    "Redundant @unique annotation on primary key field '" + field.name() + "' in model '" + modelName + "'",
                    field.findAttribute("unique").get().position()
            ));
        }

        // Duplicate field attributes (e.g. multiple @map)
        Set<String> seen = new HashSet<>();
        for (FieldAttributeNode attr : field.attributes()) {
            if (!seen.add(attr.name())) {
                diagnostics.add(LintDiagnostic.warning(
                        RULE_REDUNDANT_ANNOTATION,
                        "Duplicate attribute '@" + attr.name() + "' on field '" + field.name() + "' in model '" + modelName + "'",
                        attr.position()
                ));
            }
        }
    }

    private static void checkModelRedundantAttributes(ModelNode model, List<LintDiagnostic> diagnostics) {
        // Redundant @@index on primary key
        List<FieldNode> pkFields = model.primaryKeyFields();
        if (pkFields.size() == 1) {
            String pkName = pkFields.getFirst().name();
            for (BlockAttributeNode attr : model.indexBlockAttributes()) {
                List<String> indexedCols = extractColumnList(attr);
                if (indexedCols.size() == 1 && indexedCols.contains(pkName)) {
                    diagnostics.add(LintDiagnostic.warning(
                            RULE_REDUNDANT_ANNOTATION,
                            "Redundant @@index on primary key field '" + pkName + "' in model '" + model.name() + "'",
                            attr.position()
                    ));
                }
            }
        }

        // Duplicate block attributes (e.g. duplicate @@map)
        Set<String> seen = new HashSet<>();
        for (BlockAttributeNode attr : model.attributes()) {
            if ("map".equals(attr.name()) || "schema".equals(attr.name()) || "auth".equals(attr.name()) || "expose".equals(attr.name())) {
                if (!seen.add(attr.name())) {
                    diagnostics.add(LintDiagnostic.warning(
                            RULE_REDUNDANT_ANNOTATION,
                            "Duplicate block attribute '@@" + attr.name() + "' on model '" + model.name() + "'",
                            attr.position()
                    ));
                }
            }
        }
    }

    private static void checkForeignKeyIndexes(ModelNode model, List<LintDiagnostic> diagnostics) {
        Set<String> indexedCols = new HashSet<>();

        // Collect single field indexes and PKs
        for (FieldNode f : model.fields()) {
            if (f.isPrimaryKey() || f.isUnique()) {
                indexedCols.add(f.name());
            }
        }

        // Collect block @@id, @@unique, @@index leading columns
        for (BlockAttributeNode attr : model.attributes()) {
            if ("id".equals(attr.name()) || "unique".equals(attr.name()) || "index".equals(attr.name())) {
                List<String> cols = extractColumnList(attr);
                if (!cols.isEmpty()) {
                    // In B-Tree indexes, having the column as the prefix of an index satisfies lookups
                    indexedCols.add(cols.getFirst());
                }
            }
        }

        // Inspect @relation(fields: [...])
        for (FieldNode field : model.fields()) {
            Optional<FieldAttributeNode> relOpt = field.relationAttribute();
            if (relOpt.isPresent()) {
                FieldAttributeNode rel = relOpt.get();
                Optional<ExpressionNode> fieldsArg = rel.getArgumentValue("fields");
                if (fieldsArg.isPresent() && fieldsArg.get() instanceof ArrayLiteralNode arr) {
                    for (ExpressionNode elem : arr.elements()) {
                        String col = extractIdent(elem);
                        if (col != null && !indexedCols.contains(col)) {
                            diagnostics.add(LintDiagnostic.warning(
                                    RULE_MISSING_FK_INDEX,
                                    "Foreign key field '" + col + "' referenced by relation '" + field.name() +
                                            "' in model '" + model.name() + "' is not indexed. Consider adding an index for optimal JOIN performance.",
                                    field.position()
                            ));
                        }
                    }
                }
            }
        }
    }

    private static List<String> extractColumnList(BlockAttributeNode attr) {
        Optional<ExpressionNode> expr = attr.getPositionalArgumentValue(0)
                .or(() -> attr.getArgumentValue("fields"));
        if (expr.isPresent() && expr.get() instanceof ArrayLiteralNode arr) {
            List<String> list = new ArrayList<>();
            for (ExpressionNode e : arr.elements()) {
                String id = extractIdent(e);
                if (id != null) list.add(id);
            }
            return list;
        }
        return List.of();
    }

    private static String extractIdent(ExpressionNode expr) {
        if (expr instanceof IdentifierNode id) {
            return id.name();
        } else if (expr instanceof StringLiteralNode str) {
            return str.value();
        }
        return null;
    }
}
